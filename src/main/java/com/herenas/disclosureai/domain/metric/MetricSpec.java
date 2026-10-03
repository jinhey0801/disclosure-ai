package com.herenas.disclosureai.domain.metric;

import java.util.Set;
import java.util.stream.Collectors;

/** 정답 매핑·추출에 필요한 항목 정보만 뽑은 값. 엔티티에 의존하지 않아 단위 테스트가 쉽다. */
public record MetricSpec(String code, String standardName, StatementType statementType, String dartAccountId,
		Set<String> synonyms) {

	public static MetricSpec from(MetricDefinition metric) {
		return new MetricSpec(metric.getCode(), metric.getStandardName(), metric.getStatementType(),
				metric.getDartAccountId(),
				metric.getSynonyms().stream().map(MetricSynonym::getSynonym).collect(Collectors.toSet()));
	}
}
