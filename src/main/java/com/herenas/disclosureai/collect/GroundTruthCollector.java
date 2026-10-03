package com.herenas.disclosureai.collect;

import com.herenas.disclosureai.collect.GroundTruthMapper.MappedValue;
import com.herenas.disclosureai.dart.DartClient;
import com.herenas.disclosureai.dart.DartResponses.Account;
import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.groundtruth.GroundTruth;
import com.herenas.disclosureai.domain.groundtruth.GroundTruthRepository;
import com.herenas.disclosureai.domain.metric.MetricDefinition;
import com.herenas.disclosureai.domain.metric.MetricDefinitionRepository;
import com.herenas.disclosureai.domain.metric.MetricSpec;
import com.herenas.disclosureai.domain.metric.StatementType;
import com.herenas.disclosureai.domain.report.DisclosureReport;
import com.herenas.disclosureai.domain.report.DisclosureReportRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보고서 하나의 정답(DART 재무제표 API 값)을 수집해 ground_truth 를 교체한다. */
@Service
@RequiredArgsConstructor
public class GroundTruthCollector {

	private static final List<FsDiv> FS_DIVS = List.of(FsDiv.CFS, FsDiv.OFS);

	private final DartClient dartClient;
	private final GroundTruthMapper mapper;
	private final DisclosureReportRepository reportRepository;
	private final MetricDefinitionRepository metricRepository;
	private final GroundTruthRepository groundTruthRepository;

	public record Result(int saved, List<String> warnings) {
	}

	@Transactional
	public Result collect(Long reportId) {
		DisclosureReport report = reportRepository.findById(reportId).orElseThrow();
		List<MetricDefinition> metrics = metricRepository.findByEnabledTrue();
		Map<String, MetricDefinition> metricByCode = metrics.stream()
				.collect(Collectors.toMap(MetricDefinition::getCode, Function.identity()));
		List<MetricSpec> specs = metrics.stream().map(MetricSpec::from).toList();

		List<GroundTruth> truths = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		for (FsDiv fsDiv : FS_DIVS) {
			List<Account> accounts = dartClient.financialStatement(report.getCompany().getCorpCode(),
					report.getFiscalYear(), report.getReportType().dartCode(), fsDiv);
			if (accounts.isEmpty()) {
				continue; // 연결재무제표가 없는 회사 등
			}
			String dartRceptNo = accounts.get(0).rceptNo();
			if (!report.getRceptNo().equals(dartRceptNo)) {
				warnings.add(label(report, fsDiv) + " 접수번호 불일치(보고서 " + report.getRceptNo() + ", API "
						+ dartRceptNo + ") - 보고서 목록을 다시 동기화해야 함");
				continue;
			}
			List<MappedValue> mapped = mapper.map(accounts, specs, report.getReportType());
			for (MappedValue value : mapped) {
				truths.add(GroundTruth.builder()
						.report(report)
						.metric(metricByCode.get(value.metricCode()))
						.fsDiv(fsDiv)
						.periodScope(value.periodScope())
						.value(value.value())
						.source(GroundTruth.Source.DART_FS_API)
						.sourceAccountId(value.accountId())
						.sourceAccountName(value.accountName())
						.build());
			}
			warnings.addAll(missingMetrics(report, fsDiv, metrics, mapped));
		}

		groundTruthRepository.deleteByReport(report);
		groundTruthRepository.saveAll(truths);
		return new Result(truths.size(), warnings);
	}

	/** 평가 대상인데 정답을 찾지 못한 항목. 평가셋 품질 확인용. */
	private static List<String> missingMetrics(DisclosureReport report, FsDiv fsDiv, List<MetricDefinition> metrics,
			List<MappedValue> mapped) {
		Set<String> found = mapped.stream().map(MappedValue::metricCode).collect(Collectors.toSet());
		return metrics.stream()
				.filter(MetricDefinition::isEvaluated)
				.filter(m -> m.getStatementType() != StatementType.GENERAL)
				.filter(m -> !found.contains(m.getCode()))
				.map(m -> label(report, fsDiv) + " 정답 없음: " + m.getStandardName())
				.toList();
	}

	private static String label(DisclosureReport report, FsDiv fsDiv) {
		return report.getFiscalYear() + " " + report.getReportType() + " " + fsDiv;
	}
}
