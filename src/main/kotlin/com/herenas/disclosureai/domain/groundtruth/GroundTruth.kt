package com.herenas.disclosureai.domain.groundtruth

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.metric.MetricDefinition
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
import java.math.BigDecimal
import java.time.LocalDateTime

/** DART Open API 에서 수집한 정답. ExtractionResult 와 같은 키로 비교한다. */
@Entity
class GroundTruth(
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id")
	val report: DisclosureReport,

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "metric_code")
	val metric: MetricDefinition,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 4)
	val fsDiv: FsDiv,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	val periodScope: PeriodScope,

	/** 원/명 단위 */
	@Column(nullable = false, precision = 20, scale = 0)
	val value: BigDecimal,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	val source: Source,

	@Column(length = 100)
	val sourceAccountId: String?,

	@Column(length = 200)
	val sourceAccountName: String?,
) {
	enum class Source { DART_FS_API, DART_EMP_API }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null
		protected set

	@Column(nullable = false)
	var fetchedAt: LocalDateTime = LocalDateTime.now()
		protected set
}

interface GroundTruthRepository : JpaRepository<GroundTruth, Long> {

	fun findByReportId(reportId: Long): List<GroundTruth>

	@Modifying
	@Query("delete from GroundTruth g where g.report = :report")
	fun deleteByReport(report: DisclosureReport)
}
