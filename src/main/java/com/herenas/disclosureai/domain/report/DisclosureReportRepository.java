package com.herenas.disclosureai.domain.report;

import com.herenas.disclosureai.domain.company.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisclosureReportRepository extends JpaRepository<DisclosureReport, Long> {

	Optional<DisclosureReport> findByRceptNo(String rceptNo);

	Optional<DisclosureReport> findByCompanyAndFiscalYearAndReportType(Company company, Integer fiscalYear,
			ReportType reportType);

	List<DisclosureReport> findByCompanyOrderByPeriodEndAsc(Company company);
}
