package com.herenas.disclosureai.domain.document;

import com.herenas.disclosureai.domain.report.DisclosureReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReportSectionRepository extends JpaRepository<ReportSection, Long> {

	List<ReportSection> findByReportIdOrderByFsDivAscStatementTypeAscSeqAsc(Long reportId);

	boolean existsByReport(DisclosureReport report);

	@Modifying
	@Query("delete from ReportSection s where s.report = :report")
	void deleteByReport(DisclosureReport report);
}
