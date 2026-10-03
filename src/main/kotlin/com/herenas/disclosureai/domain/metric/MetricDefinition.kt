package com.herenas.disclosureai.domain.metric

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
import jakarta.persistence.OneToMany
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

enum class StatementType {
	/** 재무상태표 */
	BS,

	/** 손익계산서 */
	IS,

	/** 재무제표 외 일반 항목 */
	GENERAL,
}

/** 항목 성격. DURATION 항목만 3개월/누적(PeriodScope) 구분이 필요하다. */
enum class PeriodBasis { INSTANT, DURATION }

/** 정규화 단위 */
enum class CanonicalUnit { KRW, PERSON }

/** 추출 항목 메타데이터. 데이터는 Flyway 시드(V2)로 관리한다. */
@Entity
class MetricDefinition(
	@Id
	@Column(length = 40)
	val code: String,

	@Column(nullable = false, length = 50)
	val standardName: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	val statementType: StatementType,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	val periodBasis: PeriodBasis,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	val canonicalUnit: CanonicalUnit,

	@Column(length = 100)
	val dartAccountId: String?,

	@Column(nullable = false)
	val enabled: Boolean,

	@Column(nullable = false)
	val evaluated: Boolean,

	@Column(length = 500)
	val description: String?,
) {
	@OneToMany(mappedBy = "metric")
	val synonyms: MutableList<MetricSynonym> = mutableListOf()
}

@Entity
class MetricSynonym(
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "metric_code")
	val metric: MetricDefinition,

	@Column(nullable = false, length = 100)
	val synonym: String,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null
		protected set
}

/** 정답 매핑·추출에 필요한 항목 정보만 뽑은 값. 엔티티에 의존하지 않아 단위 테스트가 쉽다. */
data class MetricSpec(
	val code: String,
	val standardName: String,
	val statementType: StatementType,
	val dartAccountId: String?,
	val synonyms: Set<String>,
) {
	companion object {
		fun from(metric: MetricDefinition) = MetricSpec(
			metric.code, metric.standardName, metric.statementType, metric.dartAccountId,
			metric.synonyms.map { it.synonym }.toSet(),
		)
	}
}

interface MetricDefinitionRepository : JpaRepository<MetricDefinition, String> {

	@EntityGraph(attributePaths = ["synonyms"])
	fun findByEnabledTrue(): List<MetricDefinition>
}
