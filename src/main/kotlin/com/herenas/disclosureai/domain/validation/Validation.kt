package com.herenas.disclosureai.domain.validation

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.extraction.ExtractionRun
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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

/** 검증 규칙. 규칙의 "종류"는 코드(RuleType)로, 대상 항목과 임계값은 params(JSON)로 둔다. 데이터는 Flyway 로 관리. */
@Entity
class ValidationRule(
	@Id
	@Column(length = 40)
	val code: String,

	@Column(nullable = false, length = 100)
	val name: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	val ruleType: RuleType,

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	val params: Map<String, Any>,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	val severity: Severity,

	@Column(nullable = false)
	val enabled: Boolean,

	@Column(length = 500)
	val description: String?,
) {
	enum class RuleType { BALANCE_IDENTITY, CAPITAL_IMPAIRMENT, PERIOD_CHANGE, MIN_SCALE }

	enum class Severity { ERROR, WARNING }
}

@Entity
class ValidationResult(
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id")
	val run: ExtractionRun,

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id")
	val report: DisclosureReport,

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rule_code")
	val rule: ValidationRule,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 4)
	val fsDiv: FsDiv,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	val outcome: Outcome,

	@Column(length = 500)
	val message: String?,

	@JdbcTypeCode(SqlTypes.JSON)
	val details: Map<String, Any>?,
) {
	enum class Outcome { PASS, FAIL, SKIPPED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null
		protected set

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	var createdAt: LocalDateTime? = null
		protected set
}

interface ValidationRuleRepository : JpaRepository<ValidationRule, String> {

	fun findByEnabledTrue(): List<ValidationRule>
}

interface ValidationResultRepository : JpaRepository<ValidationResult, Long> {

	fun findByRunIdAndReportId(runId: Long, reportId: Long): List<ValidationResult>

	@EntityGraph(attributePaths = ["rule"])
	fun findByRunId(runId: Long): List<ValidationResult>
}
