package com.herenas.disclosureai.validation;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.extraction.ExtractionRun;
import com.herenas.disclosureai.domain.report.DisclosureReport;
import com.herenas.disclosureai.domain.report.ReportType;
import com.herenas.disclosureai.domain.validation.ValidationResult;
import com.herenas.disclosureai.domain.validation.ValidationResult.Outcome;
import com.herenas.disclosureai.domain.validation.ValidationResultRepository;
import com.herenas.disclosureai.domain.validation.ValidationRule;
import com.herenas.disclosureai.domain.validation.ValidationRuleRepository;
import com.herenas.disclosureai.validation.ValidationEngine.Snapshot;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 검증 규칙을 정답 또는 추출 실행 결과에 적용한다. */
@Service
@RequiredArgsConstructor
public class ValidationService {

	private final EntityManager em;
	private final ValidationEngine engine;
	private final ValidationRuleRepository ruleRepository;
	private final ValidationResultRepository resultRepository;

	public record Item(Long reportId, String company, int fiscalYear, ReportType reportType, FsDiv fsDiv,
			String ruleCode, String ruleName, ValidationRule.Severity severity, Outcome outcome, String message,
			Map<String, Object> details) {
	}

	public record Summary(Map<String, Map<Outcome, Long>> countsByRule, List<Item> failures) {

		static Summary of(List<Item> items) {
			Map<String, Map<Outcome, Long>> counts = items.stream().collect(Collectors.groupingBy(Item::ruleCode,
					TreeMap::new, Collectors.groupingBy(Item::outcome, TreeMap::new, Collectors.counting())));
			List<Item> failures = items.stream().filter(i -> i.outcome() == Outcome.FAIL)
					.sorted(Comparator.comparing(Item::company).thenComparing(Item::fiscalYear)
							.thenComparing(Item::reportType))
					.toList();
			return new Summary(counts, failures);
		}
	}

	private record Key(Long reportId, FsDiv fsDiv) {
	}

	private record ReportMeta(Long companyId, String company, int fiscalYear, ReportType reportType) {
	}

	/** 정답 자체에 규칙을 돌려 본다 (저장하지 않음). 규칙이 맞게 동작하는지와 데이터 특성 확인용. */
	@Transactional(readOnly = true)
	public Summary validateGroundTruth() {
		return Summary.of(run(load("""
				select g.report.id, g.metric.code, g.fsDiv, g.periodScope, g.value from GroundTruth g
				""", null)));
	}

	/** 추출 실행 결과에 규칙을 돌려 validation_result 에 저장한다. */
	@Transactional
	public Summary validateRun(Long runId) {
		List<Item> items = run(load("""
				select r.report.id, r.metric.code, r.fsDiv, r.periodScope, r.normalizedValue
				from ExtractionResult r where r.run.id = :runId and r.normalizedValue is not null
				""", runId));
		em.createQuery("delete from ValidationResult v where v.run.id = :runId")
				.setParameter("runId", runId).executeUpdate();
		ExtractionRun run = em.getReference(ExtractionRun.class, runId);
		Map<String, ValidationRule> rules = ruleRepository.findAll().stream()
				.collect(Collectors.toMap(ValidationRule::getCode, r -> r));
		resultRepository.saveAll(items.stream()
				.map(i -> new ValidationResult(run, em.getReference(DisclosureReport.class, i.reportId()),
						rules.get(i.ruleCode()), i.fsDiv(), i.outcome(), truncate(i.message()), i.details()))
				.toList());
		return Summary.of(items);
	}

	@Transactional(readOnly = true)
	public Summary storedResults(Long runId) {
		Map<Long, ReportMeta> meta = reportMeta();
		return Summary.of(resultRepository.findByRunId(runId).stream()
				.map(v -> {
					ReportMeta m = meta.get(v.getReport().getId());
					return new Item(v.getReport().getId(), m.company(), m.fiscalYear(), m.reportType(), v.getFsDiv(),
							v.getRule().getCode(), v.getRule().getName(), v.getRule().getSeverity(), v.getOutcome(),
							v.getMessage(), v.getDetails());
				})
				.toList());
	}

	private Map<Key, Snapshot> load(String jpql, Long runId) {
		var query = em.createQuery(jpql, Object[].class);
		if (runId != null) {
			query.setParameter("runId", runId);
		}
		Map<Key, Map<String, BigDecimal>> values = new HashMap<>();
		for (Object[] r : query.getResultList()) {
			values.computeIfAbsent(new Key((Long) r[0], (FsDiv) r[2]), k -> new HashMap<>())
					.put(Snapshot.key((String) r[1], (PeriodScope) r[3]), (BigDecimal) r[4]);
		}
		Map<Key, Snapshot> snapshots = new LinkedHashMap<>();
		values.forEach((k, v) -> snapshots.put(k, new Snapshot(v)));
		return snapshots;
	}

	private List<Item> run(Map<Key, Snapshot> snapshots) {
		Map<Long, ReportMeta> meta = reportMeta();
		// 전년 동기 보고서 찾기: (회사, 연도, 종류, 연결/별도) → 보고서
		Map<String, Key> byPeriod = new HashMap<>();
		snapshots.keySet().forEach(k -> {
			ReportMeta m = meta.get(k.reportId());
			byPeriod.put(periodKey(m.companyId(), m.fiscalYear(), m.reportType(), k.fsDiv()), k);
		});
		List<ValidationRule> rules = ruleRepository.findByEnabledTrue();
		List<Item> items = new ArrayList<>();
		snapshots.forEach((key, snapshot) -> {
			ReportMeta m = meta.get(key.reportId());
			Key priorKey = byPeriod.get(periodKey(m.companyId(), m.fiscalYear() - 1, m.reportType(), key.fsDiv()));
			Snapshot prior = priorKey == null ? null : snapshots.get(priorKey);
			for (ValidationRule rule : rules) {
				ValidationEngine.Result r = engine.evaluate(rule.getRuleType(), rule.getParams(), snapshot, prior);
				items.add(new Item(key.reportId(), m.company(), m.fiscalYear(), m.reportType(), key.fsDiv(),
						rule.getCode(), rule.getName(), rule.getSeverity(), r.outcome(), r.message(), r.details()));
			}
		});
		return items;
	}

	private Map<Long, ReportMeta> reportMeta() {
		return em.createQuery("""
				select r.id, r.company.id, r.company.corpName, r.fiscalYear, r.reportType from DisclosureReport r
				""", Object[].class).getResultList().stream()
				.collect(Collectors.toMap(r -> (Long) r[0],
						r -> new ReportMeta((Long) r[1], (String) r[2], (Integer) r[3], (ReportType) r[4])));
	}

	private static String periodKey(Long companyId, int year, ReportType type, FsDiv fsDiv) {
		return companyId + ":" + year + ":" + type + ":" + fsDiv;
	}

	private static String truncate(String message) {
		return message != null && message.length() > 500 ? message.substring(0, 497) + "..." : message;
	}
}
