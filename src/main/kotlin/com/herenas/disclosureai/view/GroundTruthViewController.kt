package com.herenas.disclosureai.view

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.company.CompanyRepository
import com.herenas.disclosureai.domain.metric.MetricDefinitionRepository
import com.herenas.disclosureai.domain.report.DisclosureReportRepository
import com.herenas.disclosureai.domain.report.ReportType
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate

/** 조회 화면(static/index.html)용 읽기 전용 API */
@RestController
@RequestMapping("/api/companies")
@Transactional(readOnly = true)
class GroundTruthViewController(
	private val em: EntityManager,
	private val companyRepository: CompanyRepository,
	private val reportRepository: DisclosureReportRepository,
	private val metricRepository: MetricDefinitionRepository,
) {
	data class CompanyRow(val corpCode: String, val corpName: String, val stockCode: String?, val reports: Long, val groundTruths: Long)

	data class ReportColumn(
		val id: Long,
		val fiscalYear: Int,
		val reportType: ReportType,
		val periodEnd: LocalDate,
		val rceptNo: String,
		val title: String,
	)

	data class MetricRow(
		val code: String,
		val standardName: String,
		val statementType: String,
		val evaluated: Boolean,
		val dartAccountId: String?,
	)

	data class Value(
		val reportId: Long,
		val metricCode: String,
		val fsDiv: FsDiv,
		val periodScope: PeriodScope,
		val value: BigDecimal,
		val sourceAccountId: String?,
		val sourceAccountName: String?,
	)

	data class CompanyDetail(
		val company: CompanyRow,
		val reports: List<ReportColumn>,
		val metrics: List<MetricRow>,
		val values: List<Value>,
	)

	@GetMapping
	fun companies(): List<CompanyRow> =
		em.createQuery(
			"""
			select c.corpCode, c.corpName, c.stockCode, count(distinct r.id), count(g.id)
			from Company c
			left join DisclosureReport r on r.company = c
			left join GroundTruth g on g.report = r
			group by c.id, c.corpCode, c.corpName, c.stockCode
			order by c.corpName
			""".trimIndent(),
			Array<Any?>::class.java,
		).resultList.map {
			CompanyRow(it[0] as String, it[1] as String, it[2] as String?, it[3] as Long, it[4] as Long)
		}

	@GetMapping("/{corpCode}")
	fun company(@PathVariable corpCode: String): CompanyDetail {
		val company = companyRepository.findByCorpCode(corpCode) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
		val row = companies().first { it.corpCode == corpCode }
		val reports = reportRepository.findByCompanyOrderByPeriodEndAsc(company).map {
			ReportColumn(it.id!!, it.fiscalYear, it.reportType, it.periodEnd, it.rceptNo, it.title)
		}
		val metrics = metricRepository.findAll()
			.filter { it.enabled }
			.sortedBy { it.statementType.ordinal }
			.map { MetricRow(it.code, it.standardName, it.statementType.name, it.evaluated, it.dartAccountId) }
		val values = em.createQuery(
			"""
			select g.report.id, g.metric.code, g.fsDiv, g.periodScope, g.value, g.sourceAccountId, g.sourceAccountName
			from GroundTruth g
			where g.report.company = :company
			""".trimIndent(),
			Array<Any?>::class.java,
		).setParameter("company", company).resultList.map {
			Value(
				it[0] as Long, it[1] as String, it[2] as FsDiv, it[3] as PeriodScope, it[4] as BigDecimal,
				it[5] as String?, it[6] as String?,
			)
		}
		return CompanyDetail(row, reports, metrics, values)
	}
}
