package com.herenas.disclosureai.domain.report

import com.herenas.disclosureai.domain.company.Company
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.time.LocalDateTime

/** 정기공시 보고서 종류. dartCode 는 DART API 의 reprt_code. */
enum class ReportType(val dartCode: String) {
	Q1("11013"),
	HALF("11012"),
	Q3("11014"),
	ANNUAL("11011"),
	;

	companion object {
		fun fromDartCode(dartCode: String): ReportType =
			entries.firstOrNull { it.dartCode == dartCode }
				?: throw IllegalArgumentException("Unknown reprt_code: $dartCode")
	}
}

@Entity
class DisclosureReport(
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id")
	val company: Company,

	rceptNo: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	val reportType: ReportType,

	@Column(nullable = false)
	val fiscalYear: Int,

	@Column(nullable = false)
	val periodEnd: LocalDate,

	title: String,

	filedOn: LocalDate,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null
		protected set

	/** DART 접수번호. 정정공시는 접수번호가 따로 나온다. */
	@Column(nullable = false, unique = true, length = 14)
	var rceptNo: String = rceptNo
		protected set

	@Column(nullable = false, length = 200)
	var title: String = title
		protected set

	@Column(nullable = false)
	var filedOn: LocalDate = filedOn
		protected set

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	var createdAt: LocalDateTime? = null
		protected set

	/** 정정공시가 나오면 같은 기간 보고서를 최신 접수번호로 갱신한다. */
	fun amend(rceptNo: String, title: String, filedOn: LocalDate) {
		this.rceptNo = rceptNo
		this.title = title
		this.filedOn = filedOn
	}
}

interface DisclosureReportRepository : JpaRepository<DisclosureReport, Long> {

	fun findByRceptNo(rceptNo: String): DisclosureReport?

	fun findByCompanyAndFiscalYearAndReportType(company: Company, fiscalYear: Int, reportType: ReportType): DisclosureReport?

	fun findByCompanyOrderByPeriodEndAsc(company: Company): List<DisclosureReport>
}
