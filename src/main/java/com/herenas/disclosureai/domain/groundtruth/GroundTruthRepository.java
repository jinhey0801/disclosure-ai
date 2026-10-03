package com.herenas.disclosureai.domain.groundtruth;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroundTruthRepository extends JpaRepository<GroundTruth, Long> {

	List<GroundTruth> findByReportId(Long reportId);
}
