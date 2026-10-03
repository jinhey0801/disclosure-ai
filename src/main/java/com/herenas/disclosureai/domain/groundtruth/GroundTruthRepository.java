package com.herenas.disclosureai.domain.groundtruth;

import com.herenas.disclosureai.domain.report.DisclosureReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GroundTruthRepository extends JpaRepository<GroundTruth, Long> {

	List<GroundTruth> findByReportId(Long reportId);

	@Modifying
	@Query("delete from GroundTruth g where g.report = :report")
	void deleteByReport(DisclosureReport report);
}
