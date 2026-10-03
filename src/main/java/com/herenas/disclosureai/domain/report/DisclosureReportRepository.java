package com.herenas.disclosureai.domain.report;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisclosureReportRepository extends JpaRepository<DisclosureReport, Long> {

	Optional<DisclosureReport> findByRceptNo(String rceptNo);
}
