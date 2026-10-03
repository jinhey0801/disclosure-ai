package com.herenas.disclosureai.domain.extraction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractionRunRepository extends JpaRepository<ExtractionRun, Long> {

	boolean existsByStatus(ExtractionRun.Status status);

	List<ExtractionRun> findByStatus(ExtractionRun.Status status);

	List<ExtractionRun> findAllByOrderByIdDesc();
}
