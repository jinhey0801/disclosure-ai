package com.herenas.disclosureai.domain.metric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 추출 항목 메타데이터. 데이터는 Flyway 시드(V2)로 관리한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetricDefinition {

	@Id
	@Column(length = 40)
	private String code;

	@Column(nullable = false, length = 50)
	private String standardName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private StatementType statementType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PeriodBasis periodBasis;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private CanonicalUnit canonicalUnit;

	@Column(length = 100)
	private String dartAccountId;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false)
	private boolean evaluated;

	@Column(length = 500)
	private String description;

	@OneToMany(mappedBy = "metric")
	private List<MetricSynonym> synonyms = new ArrayList<>();
}
