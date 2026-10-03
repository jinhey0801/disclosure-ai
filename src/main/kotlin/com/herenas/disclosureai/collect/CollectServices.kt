package com.herenas.disclosureai.collect

import com.herenas.disclosureai.collect.ReportTitleParser.ParsedTitle
import com.herenas.disclosureai.dart.DartClient
import com.herenas.disclosureai.dart.DartResponses
import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.company.Company
import com.herenas.disclosureai.domain.company.CompanyRepository
import com.herenas.disclosureai.domain.groundtruth.GroundTruth
import com.herenas.disclosureai.domain.groundtruth.GroundTruthRepository
import com.herenas.disclosureai.domain.metric.MetricDefinitionRepository
import com.herenas.disclosureai.domain.metric.MetricSpec
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.domain.report.DisclosureReport
import com.herenas.disclosureai.domain.report.DisclosureReportRepository
import com.herenas.disclosureai.support.SingleRunJob
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** DART 정기공시 목록을 disclosure_report 에 반영한다. */
@Service
class ReportSyncService(
	private val dartClient: DartClient,
	private val reportRepository: DisclosureReportRepository,
) {
	@Transactional
	fun sync(company: Company, from: LocalDate, to: LocalDate): List<DisclosureReport> {
		val byPeriod = dartClient.periodicReports(company.corpCode, from, to).mapNotNull { dart ->
			val parsed = ReportTitleParser.parse(dart.reportNm)
			if (parsed == null) log.warn("보고서 제목을 해석하지 못함: {} {}", company.corpName, dart.reportNm)
			parsed?.let { it to dart }
		}.groupBy({ it.first }, { it.second })

		byPeriod.forEach { (title, filings) -> upsert(company, choose(filings), title) }
		return reportRepository.findByCompanyOrderByPeriodEndAsc(company)
	}

	private fun upsert(company: Company, dart: DartResponses.Report, title: ParsedTitle) {
		val filedOn = LocalDate.parse(dart.rceptDt, DateTimeFormatter.BASIC_ISO_DATE)
		val existing = reportRepository.findByCompanyAndFiscalYearAndReportType(company, title.fiscalYear, title.reportType)
		if (existing == null) {
			reportRepository.save(
				DisclosureReport(company, dart.rceptNo, title.reportType, title.fiscalYear, title.periodEnd, dart.reportNm, filedOn),
			)
		} else if (existing.rceptNo != dart.rceptNo) {
			log.info("정정공시 반영: {} {} {} -> {}", company.corpName, dart.reportNm, existing.rceptNo, dart.rceptNo)
			existing.amend(dart.rceptNo, dart.reportNm, filedOn)
		}
	}

	companion object {
		private val log = LoggerFactory.getLogger(ReportSyncService::class.java)

		/**
		 * 같은 기간 공시 중 재무제표가 담긴 것을 고른다: 일반 공시(원본·기재정정) 중 접수번호가 가장 큰 최신본.
		 * 첨부만 고친 공시는 원본이 따로 있으면 버리고, DART 목록에 그것뿐이면(원본에 첨부를 추가한 경우) 그대로 쓴다.
		 */
		internal fun choose(filings: List<DartResponses.Report>): DartResponses.Report {
			val (attachmentOnly, regular) = filings.partition { ATTACHMENT_ONLY.containsMatchIn(it.reportNm) }
			return regular.maxByOrNull { it.rceptNo } ?: attachmentOnly.maxBy { it.rceptNo }
		}

		/**
		 * 첨부서류만 고친 정정공시. 본문(재무제표)은 원본 공시에 그대로 있고,
		 * DART 재무제표 API 도 원본 접수번호 기준 값을 준다.
		 */
		private val ATTACHMENT_ONLY = Regex("^\\[(첨부정정|첨부추가)]")
	}
}

/** 보고서 하나의 정답(DART 재무제표 API 값)을 수집해 ground_truth 를 교체한다. */
@Service
class GroundTruthCollector(
	private val dartClient: DartClient,
	private val mapper: GroundTruthMapper,
	private val reportRepository: DisclosureReportRepository,
	private val metricRepository: MetricDefinitionRepository,
	private val groundTruthRepository: GroundTruthRepository,
) {
	data class Result(val saved: Int, val warnings: List<String>)

	@Transactional
	fun collect(reportId: Long): Result {
		val report = reportRepository.findByIdOrNull(reportId) ?: throw NoSuchElementException("report $reportId")
		val metrics = metricRepository.findByEnabledTrue()
		val metricByCode = metrics.associateBy { it.code }
		val specs = metrics.map(MetricSpec::from)

		val truths = mutableListOf<GroundTruth>()
		val warnings = mutableListOf<String>()
		for (fsDiv in listOf(FsDiv.CFS, FsDiv.OFS)) {
			val accounts = dartClient.financialStatement(
				report.company.corpCode, report.fiscalYear, report.reportType.dartCode, fsDiv,
			)
			if (accounts.isEmpty()) continue // 연결재무제표가 없는 회사 등
			val dartRceptNo = accounts.first().rceptNo
			if (report.rceptNo != dartRceptNo) {
				warnings += "${label(report, fsDiv)} 접수번호 불일치(보고서 ${report.rceptNo}, API $dartRceptNo) - 보고서 목록을 다시 동기화해야 함"
				continue
			}
			val mapped = mapper.map(accounts, specs, report.reportType)
			mapped.mapTo(truths) {
				GroundTruth(
					report = report,
					metric = metricByCode.getValue(it.metricCode),
					fsDiv = fsDiv,
					periodScope = it.periodScope,
					value = it.value,
					source = GroundTruth.Source.DART_FS_API,
					sourceAccountId = it.accountId,
					sourceAccountName = it.accountName,
				)
			}
			// 평가 대상인데 정답을 찾지 못한 항목. 평가셋 품질 확인용.
			val found = mapped.map { it.metricCode }.toSet()
			metrics.filter { it.evaluated && it.statementType != StatementType.GENERAL && it.code !in found }
				.mapTo(warnings) { "${label(report, fsDiv)} 정답 없음: ${it.standardName}" }
		}

		groundTruthRepository.deleteByReport(report)
		groundTruthRepository.saveAll(truths)
		return Result(truths.size, warnings)
	}

	private fun label(report: DisclosureReport, fsDiv: FsDiv) = "${report.fiscalYear} ${report.reportType} $fsDiv"
}

/**
 * 기업 단위 수집: 보고서 목록 동기화 → 보고서별 정답 수집.
 * 보고서마다 트랜잭션을 따로 둬서 한 건이 실패해도 나머지는 저장된다.
 */
@Service
class CollectService(
	private val companyRepository: CompanyRepository,
	private val reportSyncService: ReportSyncService,
	private val groundTruthCollector: GroundTruthCollector,
) {
	data class Summary(
		val corpCode: String,
		val corpName: String,
		val reports: Int,
		val groundTruths: Int,
		val warnings: List<String>,
	)

	fun collectAll(from: LocalDate): List<Summary> = companyRepository.findAll().map { collect(it, from) }

	fun collect(corpCode: String, from: LocalDate): Summary {
		val company = companyRepository.findByCorpCode(corpCode)
			?: throw IllegalArgumentException("등록되지 않은 기업: $corpCode")
		return collect(company, from)
	}

	private fun collect(company: Company, from: LocalDate): Summary {
		val reports = reportSyncService.sync(company, from, LocalDate.now())
		var saved = 0
		val warnings = mutableListOf<String>()
		for (report in reports) {
			try {
				val result = groundTruthCollector.collect(report.id!!)
				saved += result.saved
				warnings += result.warnings
			} catch (e: RuntimeException) {
				log.error("정답 수집 실패: {} {}", company.corpName, report.title, e)
				warnings += "${report.title} 수집 실패: ${e.message}"
			}
		}
		log.info("수집 완료: {} 보고서 {}건, 정답 {}건, 경고 {}건", company.corpName, reports.size, saved, warnings.size)
		return Summary(company.corpCode, company.corpName, reports.size, saved, warnings)
	}

	companion object {
		private val log = LoggerFactory.getLogger(CollectService::class.java)
	}
}

/** 전체 기업 정답 수집 (1분 이상 걸려 백그라운드로 실행) */
@Component
class CollectJob(private val collectService: CollectService) {

	private val job = SingleRunJob<List<CollectService.Summary>>("collect-job")

	fun start(from: LocalDate) = job.start { collectService.collectAll(from) }

	fun status() = job.status()

	@PreDestroy
	fun shutdown() = job.shutdown()
}
