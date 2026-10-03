package com.herenas.disclosureai.domain.validation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationResultRepository extends JpaRepository<ValidationResult, Long> {

	List<ValidationResult> findByRunIdAndReportId(Long runId, Long reportId);
}
