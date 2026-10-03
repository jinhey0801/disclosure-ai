package com.herenas.disclosureai.domain.extraction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractionResultRepository extends JpaRepository<ExtractionResult, Long> {

	List<ExtractionResult> findByRunIdAndReportId(Long runId, Long reportId);
}
