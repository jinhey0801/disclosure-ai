package com.herenas.disclosureai.domain.metric;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, String> {

	@EntityGraph(attributePaths = "synonyms")
	List<MetricDefinition> findByEnabledTrue();
}
