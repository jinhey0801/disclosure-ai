package com.herenas.disclosureai.collect;

import com.herenas.disclosureai.domain.company.Company;
import com.herenas.disclosureai.domain.company.CompanyRepository;
import com.herenas.disclosureai.domain.report.DisclosureReport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 기업 단위 수집: 보고서 목록 동기화 → 보고서별 정답 수집.
 * 보고서마다 트랜잭션을 따로 둬서 한 건이 실패해도 나머지는 저장된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectService {

	private final CompanyRepository companyRepository;
	private final ReportSyncService reportSyncService;
	private final GroundTruthCollector groundTruthCollector;

	public record Summary(String corpCode, String corpName, int reports, int groundTruths, List<String> warnings) {
	}

	public List<Summary> collectAll(LocalDate from) {
		return companyRepository.findAll().stream().map(company -> collect(company, from)).toList();
	}

	public Summary collect(String corpCode, LocalDate from) {
		Company company = companyRepository.findByCorpCode(corpCode)
				.orElseThrow(() -> new IllegalArgumentException("등록되지 않은 기업: " + corpCode));
		return collect(company, from);
	}

	private Summary collect(Company company, LocalDate from) {
		List<DisclosureReport> reports = reportSyncService.sync(company, from, LocalDate.now());
		int saved = 0;
		List<String> warnings = new ArrayList<>();
		for (DisclosureReport report : reports) {
			try {
				GroundTruthCollector.Result result = groundTruthCollector.collect(report.getId());
				saved += result.saved();
				warnings.addAll(result.warnings());
			} catch (RuntimeException e) {
				log.error("정답 수집 실패: {} {}", company.getCorpName(), report.getTitle(), e);
				warnings.add(report.getTitle() + " 수집 실패: " + e.getMessage());
			}
		}
		log.info("수집 완료: {} 보고서 {}건, 정답 {}건, 경고 {}건", company.getCorpName(), reports.size(), saved,
				warnings.size());
		return new Summary(company.getCorpCode(), company.getCorpName(), reports.size(), saved, warnings);
	}
}
