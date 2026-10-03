package com.herenas.disclosureai.document

import com.herenas.disclosureai.dart.DartClient
import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.document.ReportSection
import com.herenas.disclosureai.domain.document.ReportSectionRepository
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.domain.report.DisclosureReportRepository
import com.herenas.disclosureai.domain.report.ReportType
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.zip.ZipInputStream

/** 공시 원문을 받아 재무제표 본문을 report_section 에 저장한다. */
@Service
class DocumentService(
	private val dartClient: DartClient,
	private val parser: FinancialStatementParser,
	private val reportRepository: DisclosureReportRepository,
	private val sectionRepository: ReportSectionRepository,
	private val transactionTemplate: TransactionTemplate,
) {
	data class Result(val report: String, val sections: Int, val chars: Int, val warnings: List<String>)

	data class Summary(val fetched: Int, val skipped: Int, val failed: Int, val totalChars: Long, val warnings: List<String>)

	/** 전체 보고서. force=false 면 이미 받은 보고서는 건너뛴다. */
	fun fetchAll(force: Boolean): Summary {
		var fetched = 0
		var skipped = 0
		var failed = 0
		var chars = 0L
		val warnings = mutableListOf<String>()
		for (report in reportRepository.findAll()) {
			if (!force && sectionRepository.existsByReport(report)) {
				skipped++
				continue
			}
			try {
				val result = fetch(report.id!!)
				fetched++
				chars += result.chars
				warnings += result.warnings
			} catch (e: RuntimeException) {
				failed++
				log.error("원문 수집 실패: report {}", report.id, e)
				warnings += "${report.id} ${report.title} 실패: ${e.message}"
			}
		}
		return Summary(fetched, skipped, failed, chars, warnings)
	}

	fun fetch(reportId: Long): Result {
		val (rceptNo, label) = transactionTemplate.execute {
			val r = reportRepository.findByIdOrNull(reportId) ?: throw NoSuchElementException("report $reportId")
			r.rceptNo to "${r.company.corpName} ${r.title}"
		}!!
		// 다운로드는 트랜잭션 밖에서 (DB 커넥션을 붙잡지 않도록)
		val parsed = parser.parse(mainDocument(dartClient.document(rceptNo), rceptNo))

		transactionTemplate.executeWithoutResult {
			val managed = reportRepository.findByIdOrNull(reportId)!!
			sectionRepository.deleteByReport(managed)
			sectionRepository.saveAll(parsed.map {
				ReportSection(managed, it.fsDiv, it.statementType, it.seq, it.title, it.unitLabel, it.content, it.sourceClass)
			})
		}

		val warnings = mutableListOf<String>()
		// 별도재무제표는 모든 회사에 있어야 한다. 연결은 종속회사가 있는 회사만.
		for (type in listOf(StatementType.BS, StatementType.IS)) {
			if (parsed.none { it.fsDiv == FsDiv.OFS && it.statementType == type }) {
				warnings += "$label: 별도 $type 표를 찾지 못함"
			}
		}
		parsed.filter { it.unitLabel != null && it.unitLabel != "원" }
			.mapTo(warnings) { "$label: ${it.title} 단위 ${it.unitLabel}" }
		return Result(label, parsed.size, parsed.sumOf { it.content.length }, warnings)
	}

	companion object {
		private val log = LoggerFactory.getLogger(DocumentService::class.java)

		/** zip 안에서 본문(접수번호.xml)만 꺼낸다. 나머지는 첨부 감사보고서. */
		fun mainDocument(zip: ByteArray, rceptNo: String): String {
			ZipInputStream(ByteArrayInputStream(zip)).use { input ->
				generateSequence { input.nextEntry }.forEach { entry ->
					if (entry.name == "$rceptNo.xml") return input.readAllBytes().toString(Charsets.UTF_8)
				}
			}
			throw IllegalStateException("본문 XML 이 없음: $rceptNo")
		}
	}
}

/**
 * LLM 없이 하는 사전 점검: 정답 숫자가 LLM 에 넣을 본문 텍스트 안에 실제로 있는가.
 * 없으면 아무리 좋은 모델이라도 맞힐 수 없으므로, 추출 정확도를 보기 전에 입력 품질부터 확인한다.
 */
@Service
@Transactional(readOnly = true)
class CoverageService(private val em: EntityManager) {

	data class Miss(
		val company: String,
		val fiscalYear: Int,
		val reportType: String,
		val fsDiv: FsDiv,
		val metric: String,
		val periodScope: String,
		val value: BigDecimal,
		val reason: String,
	)

	data class Coverage(val reportsWithSections: Int, val truths: Int, val found: Int, val zeroValues: Int, val misses: List<Miss>)

	fun check(): Coverage {
		val textByKey = em.createQuery("select s from ReportSection s", ReportSection::class.java).resultList
			.groupBy({ key(it.report.id!!, it.fsDiv, it.statementType) }, { it.content })
			.mapValues { it.value.joinToString("\n") }
		val reportsWithSections = textByKey.keys.map { it.substringBefore(":") }.distinct().size

		val rows = em.createQuery(
			"""
			select g.report.id, g.report.company.corpName, g.report.fiscalYear, g.report.reportType, g.fsDiv,
			       g.metric.statementType, g.metric.code, g.periodScope, g.value
			from GroundTruth g
			""".trimIndent(),
			Array<Any>::class.java,
		).resultList

		var found = 0
		var zeros = 0
		val misses = mutableListOf<Miss>()
		for (r in rows) {
			val value = r[8] as BigDecimal
			val text = textByKey[key(r[0] as Long, r[4] as FsDiv, r[5] as StatementType)]
			fun miss(reason: String) = Miss(
				r[1] as String, r[2] as Int, (r[3] as ReportType).name, r[4] as FsDiv, r[6] as String,
				(r[7] as PeriodScope).name, value, reason,
			)
			when {
				text == null -> misses += miss("본문 표 없음")
				value.signum() == 0 -> zeros++ // 0 은 "0", "-", 빈칸 등 표기가 제각각이라 별도로 센다
				containsNumber(text, value) -> found++
				else -> misses += miss("본문에 숫자 없음")
			}
		}
		return Coverage(reportsWithSections, rows.size, found, zeros, misses)
	}

	private fun key(reportId: Long, fsDiv: FsDiv, type: StatementType) = "$reportId:$fsDiv:$type"

	companion object {
		/** 원 단위 절댓값을 천 단위 콤마로 찾는다. 음수 표기((1,234), -1,234, △1,234)와 무관하게 하려고 절댓값만 본다. */
		fun containsNumber(text: String, value: BigDecimal): Boolean {
			val formatted = DecimalFormat("#,##0").format(value.abs())
			return Regex("(?<![\\d,])" + Regex.escape(formatted) + "(?![\\d,])").containsMatchIn(text)
		}
	}
}
