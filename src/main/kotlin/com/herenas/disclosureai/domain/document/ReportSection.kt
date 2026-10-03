package com.herenas.disclosureai.domain.document

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.domain.report.DisclosureReport
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
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

/** 공시 원문에서 잘라낸 재무제표 한 개 (LLM 입력 단위) */
@Entity
class ReportSection(
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id")
	val report: DisclosureReport,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 4)
	val fsDiv: FsDiv,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	val statementType: StatementType,

	@Column(nullable = false)
	val seq: Int,

	@Column(nullable = false, length = 200)
	val title: String,

	@Column(length = 20)
	val unitLabel: String?,

	@Column(nullable = false, columnDefinition = "MEDIUMTEXT")
	val content: String,

	@Column(nullable = false, length = 30)
	val sourceClass: String,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null
		protected set

	@Column(nullable = false)
	var charCount: Int = content.length
		protected set

	@Column(nullable = false)
	var fetchedAt: LocalDateTime = LocalDateTime.now()
		protected set
}

interface ReportSectionRepository : JpaRepository<ReportSection, Long> {

	fun findByReportIdOrderByFsDivAscStatementTypeAscSeqAsc(reportId: Long): List<ReportSection>

	fun existsByReport(report: DisclosureReport): Boolean

	@Query("select distinct s.report.id from ReportSection s order by s.report.id")
	fun findReportIdsWithSections(): List<Long>

	@Modifying
	@Query("delete from ReportSection s where s.report = :report")
	fun deleteByReport(report: DisclosureReport)
}
