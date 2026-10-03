package com.herenas.disclosureai.validation

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.extraction.ExtractionRun
import com.herenas.disclosureai.domain.report.DisclosureReport
import com.herenas.disclosureai.domain.report.ReportType
import com.herenas.disclosureai.domain.validation.ValidationResult
import com.herenas.disclosureai.domain.validation.ValidationResult.Outcome
import com.herenas.disclosureai.domain.validation.ValidationResultRepository
import com.herenas.disclosureai.domain.validation.ValidationRule
import com.herenas.disclosureai.domain.validation.ValidationRuleRepository
import com.herenas.disclosureai.validation.ValidationEngine.Snapshot
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.SortedMap

/** 검증 규칙을 정답 또는 추출 실행 결과에 적용한다. */
@Service
class ValidationService(
	private val em: EntityManager,
	private val engine: ValidationEngine,
	private val ruleRepository: ValidationRuleRepository,
	private val resultRepository: ValidationResultRepository,
) {
	data class Item(
		val reportId: Long,
		val company: String,
		val fiscalYear: Int,
		val reportType: ReportType,
		val fsDiv: FsDiv,
		val ruleCode: String,
		val ruleName: String,
		val severity: ValidationRule.Severity,
		val outcome: Outcome,
		val message: String?,
		val details: Map<String, Any>?,
	)

	data class Summary(val countsByRule: SortedMap<String, SortedMap<Outcome, Int>>, val failures: List<Item>) {
		companion object {
			fun of(items: List<Item>) = Summary(
				items.groupBy { it.ruleCode }
					.mapValues { (_, v) -> v.groupingBy { it.outcome }.eachCount().toSortedMap() }
					.toSortedMap(),
				items.filter { it.outcome == Outcome.FAIL }
					.sortedWith(compareBy({ it.company }, { it.fiscalYear }, { it.reportType })),
			)
		}
	}

	private data class Key(val reportId: Long, val fsDiv: FsDiv)

	private data class ReportMeta(val companyId: Long, val company: String, val fiscalYear: Int, val reportType: ReportType)

	/** 정답 자체에 규칙을 돌려 본다 (저장하지 않음). 규칙이 맞게 동작하는지와 데이터 특성 확인용. */
	@Transactional(readOnly = true)
	fun validateGroundTruth(): Summary = Summary.of(
		run(load("select g.report.id, g.metric.code, g.fsDiv, g.periodScope, g.value from GroundTruth g", null)),
	)

	/** 추출 실행 결과에 규칙을 돌려 validation_result 에 저장한다. */
	@Transactional
	fun validateRun(runId: Long): Summary {
		val items = run(
			load(
				"select r.report.id, r.metric.code, r.fsDiv, r.periodScope, r.normalizedValue " +
					"from ExtractionResult r where r.run.id = :runId and r.normalizedValue is not null",
				runId,
			),
		)
		em.createQuery("delete from ValidationResult v where v.run.id = :runId")
			.setParameter("runId", runId).executeUpdate()
		val run = em.getReference(ExtractionRun::class.java, runId)
		val rules = ruleRepository.findAll().associateBy { it.code }
		resultRepository.saveAll(items.map {
			ValidationResult(
				run, em.getReference(DisclosureReport::class.java, it.reportId), rules.getValue(it.ruleCode),
				it.fsDiv, it.outcome, it.message?.let(::truncate), it.details,
			)
		})
		return Summary.of(items)
	}

	@Transactional(readOnly = true)
	fun storedResults(runId: Long): Summary {
		val meta = reportMeta()
		return Summary.of(resultRepository.findByRunId(runId).map { v ->
			val m = meta.getValue(v.report.id!!)
			Item(
				v.report.id!!, m.company, m.fiscalYear, m.reportType, v.fsDiv, v.rule.code, v.rule.name,
				v.rule.severity, v.outcome, v.message, v.details,
			)
		})
	}

	private fun load(jpql: String, runId: Long?): Map<Key, Snapshot> {
		val query = em.createQuery(jpql, Array<Any>::class.java)
		runId?.let { query.setParameter("runId", it) }
		return query.resultList
			.groupBy({ Key(it[0] as Long, it[2] as FsDiv) }, { Snapshot.key(it[1] as String, it[3] as PeriodScope) to it[4] as BigDecimal })
			.mapValues { Snapshot(it.value.toMap()) }
	}

	private fun run(snapshots: Map<Key, Snapshot>): List<Item> {
		val meta = reportMeta()
		// 전년 동기 보고서 찾기: (회사, 연도, 종류, 연결/별도) → 보고서
		val byPeriod = snapshots.keys.associateBy {
			val m = meta.getValue(it.reportId)
			periodKey(m.companyId, m.fiscalYear, m.reportType, it.fsDiv)
		}
		val rules = ruleRepository.findByEnabledTrue()
		return snapshots.flatMap { (key, snapshot) ->
			val m = meta.getValue(key.reportId)
			val prior = byPeriod[periodKey(m.companyId, m.fiscalYear - 1, m.reportType, key.fsDiv)]?.let(snapshots::get)
			rules.map { rule ->
				val r = engine.evaluate(rule.ruleType, rule.params, snapshot, prior)
				Item(
					key.reportId, m.company, m.fiscalYear, m.reportType, key.fsDiv, rule.code, rule.name,
					rule.severity, r.outcome, r.message, r.details,
				)
			}
		}
	}

	private fun reportMeta(): Map<Long, ReportMeta> =
		em.createQuery(
			"select r.id, r.company.id, r.company.corpName, r.fiscalYear, r.reportType from DisclosureReport r",
			Array<Any>::class.java,
		).resultList.associate {
			(it[0] as Long) to ReportMeta(it[1] as Long, it[2] as String, it[3] as Int, it[4] as ReportType)
		}

	private fun periodKey(companyId: Long, year: Int, type: ReportType, fsDiv: FsDiv) = "$companyId:$year:$type:$fsDiv"

	private fun truncate(message: String) = if (message.length > 500) message.take(497) + "..." else message
}
