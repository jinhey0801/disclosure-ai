package com.herenas.disclosureai.domain.extraction

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.common.SourceUnit
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
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDateTime

/** 추출 1회 실행 단위. 모델/프롬프트 버전별 정확도 비교의 기준이 된다. */
@Entity
class ExtractionRun(
	@Column(nullable = false, length = 100)
	val model: String,

	@Column(nullable = false, length = 50)
	val promptVersion: String,

	/** 입력 변형 (InputVariant 이름). 원문이면 ORIGINAL */
	@Column(nullable = false, length = 20)
	val inputVariant: String,

	note: String?,
) {
	enum class Status { RUNNING, COMPLETED, FAILED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null
		protected set

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	var status: Status = Status.RUNNING
		protected set

	/** Spring Batch 잡 실행 ID (진행 건수 조회용) */
	var jobExecutionId: Long? = null
		protected set

	@Column(nullable = false)
	var startedAt: LocalDateTime = LocalDateTime.now()
		protected set

	var finishedAt: LocalDateTime? = null
		protected set

	@Column(length = 500)
	var note: String? = note
		protected set

	fun attachJob(jobExecutionId: Long) {
		this.jobExecutionId = jobExecutionId
	}

	fun finish(status: Status, note: String?) {
		this.status = status
		this.finishedAt = LocalDateTime.now()
		if (note != null) {
			this.note = if (note.length > 500) note.take(497) + "..." else note
		}
	}
}

/** LLM·규칙 추출 결과. 정답과의 비교 키: (report, metric, fsDiv, periodScope) */
@Entity
class ExtractionResult(
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id")
	val run: ExtractionRun,

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

	@Column(length = 200)
	val rawAccountName: String?,

	@Column(length = 100)
	val rawValue: String?,

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	val rawUnit: SourceUnit?,

	/** 원/명 단위. null 이면 원문에서 찾지 못한 것. */
	@Column(precision = 20, scale = 0)
	val normalizedValue: BigDecimal?,

	@Column(columnDefinition = "TEXT")
	val evidenceText: String?,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null
		protected set

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	var createdAt: LocalDateTime? = null
		protected set
}

interface ExtractionRunRepository : JpaRepository<ExtractionRun, Long> {

	fun existsByStatus(status: ExtractionRun.Status): Boolean

	fun findByStatus(status: ExtractionRun.Status): List<ExtractionRun>

	fun findAllByOrderByIdDesc(): List<ExtractionRun>
}

interface ExtractionResultRepository : JpaRepository<ExtractionResult, Long> {

	fun findByRunIdAndReportId(runId: Long, reportId: Long): List<ExtractionResult>
}
