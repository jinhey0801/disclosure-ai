package com.herenas.disclosureai.evaluation

import com.herenas.disclosureai.document.CoverageService
import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.document.ReportSection
import com.herenas.disclosureai.domain.extraction.ExtractionRun
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.domain.report.ReportType
import com.herenas.disclosureai.domain.validation.ValidationResult
import com.herenas.disclosureai.domain.validation.ValidationRule
import com.herenas.disclosureai.extraction.InputVariant
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToLong

/**
 * 추출 실행 결과를 정답과 같은 키(보고서, 항목, 연결/별도, 기간 구분)로 비교한다.
 *
 * 평가 대상: 평가 항목(evaluated)이고, 그 보고서·재무제표 본문이 있고, 정답 숫자가 본문에 실제로 있는 정답.
 * 본문에 없는 정답(DART API 와 공시 본문이 다른 경우 등)은 모델이 맞힐 수 없으므로 제외하고 따로 보여준다.
 * 입력 변형(천원·백만원)으로 실행했으면 반올림 오차(반 단위) 이내를 정답으로 본다.
 */
@Service
@Transactional(readOnly = true)
class EvaluationService(private val em: EntityManager) {

	enum class Outcome { CORRECT, WRONG, MISSING }

	/** 틀린 이유 추정 */
	enum class ErrorType { SIGN, UNIT_SCALE, PERIOD_SCOPE_SWAP, FS_DIV_SWAP, VALUE }

	data class Row(
		val reportId: Long,
		val company: String,
		val corpCode: String,
		val fiscalYear: Int,
		val reportType: ReportType,
		val fsDiv: FsDiv,
		val metric: String,
		val metricName: String,
		val periodScope: PeriodScope,
		val truth: BigDecimal,
		val extracted: BigDecimal?,
		val outcome: Outcome,
		val errorType: ErrorType?,
		val rawAccountName: String?,
		val evidence: String?,
	)

	data class Bucket(val key: String, val total: Int, val correct: Int, val wrong: Int, val missing: Int, val accuracy: Double)

	data class Excluded(
		val company: String,
		val fiscalYear: Int,
		val reportType: ReportType,
		val fsDiv: FsDiv,
		val metric: String,
		val periodScope: PeriodScope,
		val truth: BigDecimal,
		val reason: String,
	)

	/**
	 * 검증 규칙이 틀린 값을 얼마나 잡았나 (정답이 없는 운영 환경에서는 검증 규칙이 유일한 신호).
	 * 단위는 (보고서, 연결/별도). 자본잠식은 오류가 아니라 사업상 경고라 제외한다.
	 */
	data class Detection(val checked: Int, val withWrongValues: Int, val detected: Int, val falseAlarms: Int)

	data class Evaluation(
		val runId: Long,
		val extractor: String,
		val version: String,
		val inputVariant: String,
		val inputVariantLabel: String,
		val status: ExtractionRun.Status,
		val overall: Bucket,
		val extra: Int,
		val byMetric: List<Bucket>,
		val byPeriodScope: List<Bucket>,
		val byFsDiv: List<Bucket>,
		val byReportType: List<Bucket>,
		val byCompany: List<Bucket>,
		val errorTypes: Map<ErrorType, Int>,
		val detection: Detection,
		val errors: List<Row>,
		val excluded: List<Excluded>,
	)

	private data class Key(val reportId: Long, val metric: String, val fsDiv: FsDiv, val scope: PeriodScope)

	private data class Truth(
		val key: Key,
		val company: String,
		val corpCode: String,
		val fiscalYear: Int,
		val reportType: ReportType,
		val metricName: String,
		val statementType: StatementType,
		val value: BigDecimal,
	)

	private data class Extracted(val value: BigDecimal?, val rawAccountName: String?, val evidence: String?)

	fun evaluate(runId: Long): Evaluation {
		val run = em.find(ExtractionRun::class.java, runId) ?: throw IllegalArgumentException("없는 실행: $runId")
		val variant = InputVariant.valueOf(run.inputVariant)
		val sectionText = sectionText()
		val allTruths = truths()
		val extracted = extracted(runId)

		val truths = mutableListOf<Truth>()
		val excluded = mutableListOf<Excluded>()
		for (t in allTruths) {
			val text = sectionText["${t.key.reportId}:${t.key.fsDiv}:${t.statementType}"] ?: continue // 원문을 받지 않은 보고서
			if (t.value.signum() != 0 && !CoverageService.containsNumber(text, t.value)) {
				excluded += Excluded(
					t.company, t.fiscalYear, t.reportType, t.key.fsDiv, t.key.metric, t.key.scope, t.value,
					"정답 숫자가 공시 본문에 없음 (DART API 와 본문 불일치)",
				)
				continue
			}
			truths += t
		}
		val truthByKey = allTruths.associateBy { it.key }

		val rows = truths.map { compare(it, extracted[it.key], truthByKey, variant.tolerance(it.statementType)) }
		val extra = extracted.keys.count { it !in truthByKey }
		val errors = rows.filter { it.outcome != Outcome.CORRECT }
			.sortedWith(compareBy({ it.company }, { it.fiscalYear }, { it.reportType }, { it.metric }))
		val errorTypes = rows.mapNotNull { it.errorType }.groupingBy { it }.eachCount()

		return Evaluation(
			runId, run.model, run.promptVersion, variant.name, variant.label, run.status, bucket("전체", rows), extra,
			buckets(rows) { it.metricName }, buckets(rows) { it.periodScope.name }, buckets(rows) { it.fsDiv.name },
			buckets(rows) { it.reportType.name }, buckets(rows) { it.company }, errorTypes, detection(runId, rows),
			errors, excluded,
		)
	}

	private fun detection(runId: Long, rows: List<Row>): Detection {
		val checked = rows.map { "${it.reportId}:${it.fsDiv}" }.toSet()
		val withWrong = rows.filter { it.outcome == Outcome.WRONG }.map { "${it.reportId}:${it.fsDiv}" }.toSet()
		val flagged = em.createQuery(
			"select v from ValidationResult v join fetch v.rule where v.run.id = :runId " +
				"and v.outcome = :fail and v.rule.ruleType <> :impairment",
			ValidationResult::class.java,
		)
			.setParameter("runId", runId)
			.setParameter("fail", ValidationResult.Outcome.FAIL)
			.setParameter("impairment", ValidationRule.RuleType.CAPITAL_IMPAIRMENT)
			.resultList
			.map { "${it.report.id}:${it.fsDiv}" }
			.filter { it in checked }
			.toSet()
		return Detection(checked.size, withWrong.size, withWrong.count { it in flagged }, flagged.count { it !in withWrong })
	}

	private fun compare(t: Truth, e: Extracted?, truthByKey: Map<Key, Truth>, tolerance: BigDecimal): Row {
		val value = e?.value
		val outcome = when {
			value == null -> Outcome.MISSING
			(t.value - value).abs() <= tolerance -> Outcome.CORRECT
			else -> Outcome.WRONG
		}
		val errorType = if (outcome == Outcome.WRONG) {
			val k = t.key
			val others = PeriodScope.entries.filter { it != k.scope }
				.mapNotNull { truthByKey[k.copy(scope = it)]?.let { truth -> "scope" to truth.value } } +
				listOfNotNull(truthByKey[k.copy(fsDiv = if (k.fsDiv == FsDiv.CFS) FsDiv.OFS else FsDiv.CFS)]?.let { "fsDiv" to it.value })
			classify(t.value, value!!, others)
		} else {
			null
		}
		return Row(
			t.key.reportId, t.company, t.corpCode, t.fiscalYear, t.reportType, t.key.fsDiv, t.key.metric, t.metricName,
			t.key.scope, t.value, value, outcome, errorType, e?.rawAccountName, e?.evidence,
		)
	}

	private fun sectionText(): Map<String, String> =
		em.createQuery("select s from ReportSection s", ReportSection::class.java).resultList
			.groupBy({ "${it.report.id}:${it.fsDiv}:${it.statementType}" }, { it.content })
			.mapValues { it.value.joinToString("\n") }

	private fun truths(): List<Truth> =
		em.createQuery(
			"""
			select g.report.id, g.metric.code, g.fsDiv, g.periodScope, g.report.company.corpName,
			       g.report.company.corpCode, g.report.fiscalYear, g.report.reportType, g.metric.standardName,
			       g.metric.statementType, g.value
			from GroundTruth g where g.metric.evaluated = true
			""".trimIndent(),
			Array<Any>::class.java,
		).resultList.map {
			Truth(
				Key(it[0] as Long, it[1] as String, it[2] as FsDiv, it[3] as PeriodScope),
				it[4] as String, it[5] as String, it[6] as Int, it[7] as ReportType, it[8] as String,
				it[9] as StatementType, it[10] as BigDecimal,
			)
		}

	private fun extracted(runId: Long): Map<Key, Extracted> =
		em.createQuery(
			"""
			select r.report.id, r.metric.code, r.fsDiv, r.periodScope, r.normalizedValue, r.rawAccountName, r.evidenceText
			from ExtractionResult r where r.run.id = :runId and r.metric.evaluated = true
			""".trimIndent(),
			Array<Any?>::class.java,
		).setParameter("runId", runId).resultList.associate {
			Key(it[0] as Long, it[1] as String, it[2] as FsDiv, it[3] as PeriodScope) to
				Extracted(it[4] as BigDecimal?, it[5] as String?, it[6] as String?)
		}

	companion object {
		/** 틀린 값이 정답과 어떤 관계인지로 원인을 추정한다. others: ("scope"|"fsDiv") to 그쪽 정답 값 */
		internal fun classify(truth: BigDecimal, extracted: BigDecimal, others: List<Pair<String, BigDecimal>>): ErrorType? {
			if (truth.compareTo(extracted) == 0) return null
			if (truth.signum() != 0 && truth.negate().compareTo(extracted) == 0) return ErrorType.SIGN
			if (truth.signum() != 0 && extracted.signum() != 0) {
				// 반올림된 입력(백만원 등)에서도 잡히도록 배율을 log10 으로 비교한다: 천 배 = 3, 백만 배 = 6
				val log = log10(extracted.abs().toDouble() / truth.abs().toDouble())
				if (listOf(3, 6, 8, -3, -6, -8).any { abs(log - it) < 0.15 }) return ErrorType.UNIT_SCALE
			}
			others.firstOrNull { it.second.compareTo(extracted) == 0 }?.let {
				return if (it.first == "scope") ErrorType.PERIOD_SCOPE_SWAP else ErrorType.FS_DIV_SWAP
			}
			return ErrorType.VALUE
		}

		private fun <K> buckets(rows: List<Row>, key: (Row) -> K): List<Bucket> =
			rows.groupBy { key(it).toString() }
				.map { (k, v) -> bucket(k, v) }
				.sortedWith(compareBy({ it.accuracy }, { it.key }))

		private fun bucket(key: String, rows: List<Row>): Bucket {
			val correct = rows.count { it.outcome == Outcome.CORRECT }
			val wrong = rows.count { it.outcome == Outcome.WRONG }
			val accuracy = if (rows.isEmpty()) 0.0 else (1000.0 * correct / rows.size).roundToLong() / 10.0
			return Bucket(key, rows.size, correct, wrong, rows.size - correct - wrong, accuracy)
		}
	}
}
