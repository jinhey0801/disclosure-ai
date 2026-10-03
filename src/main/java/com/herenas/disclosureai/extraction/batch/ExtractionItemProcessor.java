package com.herenas.disclosureai.extraction.batch;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.document.ReportSection;
import com.herenas.disclosureai.domain.document.ReportSectionRepository;
import com.herenas.disclosureai.domain.extraction.ExtractionResult;
import com.herenas.disclosureai.domain.extraction.ExtractionRun;
import com.herenas.disclosureai.domain.metric.MetricDefinition;
import com.herenas.disclosureai.domain.metric.MetricDefinitionRepository;
import com.herenas.disclosureai.domain.metric.MetricSpec;
import com.herenas.disclosureai.domain.report.DisclosureReport;
import com.herenas.disclosureai.extraction.Extractor;
import com.herenas.disclosureai.extraction.Extractor.ExtractedValue;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 보고서 하나 → 연결·별도 각각 추출기를 돌려 extraction_result 엔티티 목록으로 만든다. */
@Component
@StepScope
public class ExtractionItemProcessor implements ItemProcessor<Long, List<ExtractionResult>> {

	private final EntityManager em;
	private final ReportSectionRepository sectionRepository;
	private final MetricDefinitionRepository metricRepository;
	private final Extractor extractor;
	private final Long runId;

	public ExtractionItemProcessor(EntityManager em, ReportSectionRepository sectionRepository,
			MetricDefinitionRepository metricRepository, List<Extractor> extractors,
			@Value("#{jobParameters['runId']}") Long runId,
			@Value("#{jobParameters['extractor']}") String extractorName) {
		this.em = em;
		this.sectionRepository = sectionRepository;
		this.metricRepository = metricRepository;
		this.extractor = extractors.stream().filter(e -> e.name().equals(extractorName)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("알 수 없는 추출기: " + extractorName));
		this.runId = runId;
	}

	@Override
	public List<ExtractionResult> process(Long reportId) {
		DisclosureReport report = em.find(DisclosureReport.class, reportId);
		List<MetricDefinition> metrics = metricRepository.findByEnabledTrue();
		Map<String, MetricDefinition> metricByCode = metrics.stream()
				.collect(Collectors.toMap(MetricDefinition::getCode, Function.identity()));
		List<MetricSpec> specs = metrics.stream().map(MetricSpec::from).toList();
		List<ReportSection> sections = sectionRepository.findByReportIdOrderByFsDivAscStatementTypeAscSeqAsc(reportId);
		ExtractionRun run = em.getReference(ExtractionRun.class, runId);

		List<ExtractionResult> results = new ArrayList<>();
		for (FsDiv fsDiv : List.of(FsDiv.CFS, FsDiv.OFS)) {
			List<Extractor.Section> input = sections.stream()
					.filter(s -> s.getFsDiv() == fsDiv)
					.map(s -> new Extractor.Section(s.getStatementType(), s.getSeq(), s.getTitle(), s.getUnitLabel(),
							s.getContent()))
					.toList();
			if (input.isEmpty()) {
				continue;
			}
			List<ExtractedValue> values = extractor.extract(new Extractor.Input(report.getReportType(),
					report.getFiscalYear(), fsDiv, input, specs));
			for (ExtractedValue v : values) {
				results.add(ExtractionResult.builder()
						.run(run)
						.report(report)
						.metric(metricByCode.get(v.metricCode()))
						.fsDiv(fsDiv)
						.periodScope(v.periodScope())
						.rawAccountName(truncate(v.rawAccountName(), 200))
						.rawValue(truncate(v.rawValue(), 100))
						.rawUnit(v.rawUnit())
						.normalizedValue(v.normalizedValue())
						.evidenceText(v.evidence())
						.build());
			}
		}
		return results;
	}

	private static String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}
