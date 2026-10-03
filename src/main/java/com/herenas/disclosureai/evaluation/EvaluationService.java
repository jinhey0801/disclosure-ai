package com.herenas.disclosureai.evaluation;

import com.herenas.disclosureai.document.CoverageService;
import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.document.ReportSection;
import com.herenas.disclosureai.domain.extraction.ExtractionRun;
import com.herenas.disclosureai.domain.metric.StatementType;
import com.herenas.disclosureai.domain.report.ReportType;
import com.herenas.disclosureai.domain.validation.ValidationResult;
import com.herenas.disclosureai.domain.validation.ValidationRule;
import com.herenas.disclosureai.extraction.InputVariant;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추출 실행 결과를 정답과 같은 키(보고서, 항목, 연결/별도, 기간 구분)로 비교한다.
 *
 * 평가 대상: 평가 항목(evaluated)이고, 그 보고서·재무제표 본문이 있고, 정답 숫자가 본문에 실제로 있는 정답.
 * 본문에 없는 정답(DART API 와 공시 본문이 다른 경우 등)은 모델이 맞힐 수 없으므로 제외하고 따로 보여준다.
 * 입력 변형(천원·백만원)으로 실행했으면 반올림 오차(반 단위) 이내를 정답으로 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationService {

	private final EntityManager em;

	public enum Outcome { CORRECT, WRONG, MISSING }

	/** 틀린 이유 추정 */
	public enum ErrorType { SIGN, UNIT_SCALE, PERIOD_SCOPE_SWAP, FS_DIV_SWAP, VALUE }

	public record Row(Long reportId, String company, String corpCode, int fiscalYear, ReportType reportType,
			FsDiv fsDiv, String metric, String metricName, PeriodScope periodScope, BigDecimal truth,
			BigDecimal extracted, Outcome outcome, ErrorType errorType, String rawAccountName, String evidence) {
	}

	public record Bucket(String key, int total, int correct, int wrong, int missing, double accuracy) {
	}

	public record Excluded(String company, int fiscalYear, ReportType reportType, FsDiv fsDiv, String metric,
			PeriodScope periodScope, BigDecimal truth, String reason) {
	}

	/**
	 * 검증 규칙이 틀린 값을 얼마나 잡았나 (정답이 없는 운영 환경에서는 검증 규칙이 유일한 신호).
	 * 단위는 (보고서, 연결/별도). 자본잠식은 오류가 아니라 사업상 경고라 제외한다.
	 */
	public record Detection(int checked, int withWrongValues, int detected, int falseAlarms) {
	}

	public record Evaluation(Long runId, String extractor, String version, String inputVariant,
			String inputVariantLabel, ExtractionRun.Status status, Bucket overall, int extra, List<Bucket> byMetric,
			List<Bucket> byPeriodScope, List<Bucket> byFsDiv, List<Bucket> byReportType, List<Bucket> byCompany,
			Map<ErrorType, Long> errorTypes, Detection detection, List<Row> errors, List<Excluded> excluded) {
	}

	private record Key(Long reportId, String metric, FsDiv fsDiv, PeriodScope scope) {
	}

	private record Truth(Key key, String company, String corpCode, int fiscalYear, ReportType reportType,
			String metricName, StatementType statementType, BigDecimal value) {
	}

	private record Extracted(BigDecimal value, String rawAccountName, String evidence) {
	}

	public Evaluation evaluate(Long runId) {
		ExtractionRun run = em.find(ExtractionRun.class, runId);
		if (run == null) {
			throw new IllegalArgumentException("없는 실행: " + runId);
		}
		InputVariant variant = InputVariant.valueOf(run.getInputVariant());
		Map<String, String> sectionText = sectionText();
		List<Truth> allTruths = truths();
		Map<Key, Extracted> extracted = extracted(runId);

		List<Truth> truths = new ArrayList<>();
		List<Excluded> excluded = new ArrayList<>();
		for (Truth t : allTruths) {
			String text = sectionText.get(t.key().reportId() + ":" + t.key().fsDiv() + ":" + t.statementType());
			if (text == null) {
				continue; // 원문을 받지 않은 보고서
			}
			if (t.value().signum() != 0 && !CoverageService.containsNumber(text, t.value())) {
				excluded.add(new Excluded(t.company(), t.fiscalYear(), t.reportType(), t.key().fsDiv(),
						t.key().metric(), t.key().scope(), t.value(), "정답 숫자가 공시 본문에 없음 (DART API 와 본문 불일치)"));
				continue;
			}
			truths.add(t);
		}
		Map<Key, Truth> truthByKey = allTruths.stream().collect(Collectors.toMap(Truth::key, Function.identity()));

		List<Row> rows = truths.stream()
				.map(t -> compare(t, extracted.get(t.key()), truthByKey, variant.tolerance(t.statementType())))
				.toList();
		long extra = extracted.keySet().stream().filter(k -> !truthByKey.containsKey(k)).count();

		List<Row> errors = rows.stream().filter(r -> r.outcome() != Outcome.CORRECT)
				.sorted(Comparator.comparing(Row::company).thenComparing(Row::fiscalYear)
						.thenComparing(Row::reportType).thenComparing(Row::metric))
				.toList();
		Map<ErrorType, Long> errorTypes = rows.stream().filter(r -> r.errorType() != null)
				.collect(Collectors.groupingBy(Row::errorType, LinkedHashMap::new, Collectors.counting()));

		return new Evaluation(runId, run.getModel(), run.getPromptVersion(), variant.name(), variant.label(),
				run.getStatus(), bucket("전체", rows), (int) extra,
				buckets(rows, Row::metricName), buckets(rows, r -> r.periodScope().name()),
				buckets(rows, r -> r.fsDiv().name()), buckets(rows, r -> r.reportType().name()),
				buckets(rows, Row::company), errorTypes, detection(runId, rows), errors, excluded);
	}

	private Detection detection(Long runId, List<Row> rows) {
		Set<String> checked = rows.stream().map(r -> r.reportId() + ":" + r.fsDiv()).collect(Collectors.toSet());
		Set<String> withWrong = rows.stream().filter(r -> r.outcome() == Outcome.WRONG)
				.map(r -> r.reportId() + ":" + r.fsDiv()).collect(Collectors.toSet());
		Set<String> flagged = em.createQuery(
				"select v from ValidationResult v join fetch v.rule where v.run.id = :runId "
						+ "and v.outcome = :fail and v.rule.ruleType <> :impairment", ValidationResult.class)
				.setParameter("runId", runId)
				.setParameter("fail", ValidationResult.Outcome.FAIL)
				.setParameter("impairment", ValidationRule.RuleType.CAPITAL_IMPAIRMENT)
				.getResultList().stream()
				.map(v -> v.getReport().getId() + ":" + v.getFsDiv())
				.filter(checked::contains)
				.collect(Collectors.toSet());
		int detected = (int) withWrong.stream().filter(flagged::contains).count();
		int falseAlarms = (int) flagged.stream().filter(k -> !withWrong.contains(k)).count();
		return new Detection(checked.size(), withWrong.size(), detected, falseAlarms);
	}

	private Row compare(Truth t, Extracted e, Map<Key, Truth> truthByKey, BigDecimal tolerance) {
		BigDecimal value = e == null ? null : e.value();
		Outcome outcome = value == null ? Outcome.MISSING
				: t.value().subtract(value).abs().compareTo(tolerance) <= 0 ? Outcome.CORRECT : Outcome.WRONG;
		ErrorType type = null;
		if (outcome == Outcome.WRONG) {
			Map<String, BigDecimal> others = new HashMap<>();
			Key k = t.key();
			for (PeriodScope scope : PeriodScope.values()) {
				if (scope != k.scope()) {
					put(others, "scope", truthByKey.get(new Key(k.reportId(), k.metric(), k.fsDiv(), scope)));
				}
			}
			FsDiv other = k.fsDiv() == FsDiv.CFS ? FsDiv.OFS : FsDiv.CFS;
			put(others, "fsDiv", truthByKey.get(new Key(k.reportId(), k.metric(), other, k.scope())));
			type = classify(t.value(), value, others);
		}
		return new Row(t.key().reportId(), t.company(), t.corpCode(), t.fiscalYear(), t.reportType(), t.key().fsDiv(),
				t.key().metric(), t.metricName(), t.key().scope(), t.value(), value, outcome, type,
				e == null ? null : e.rawAccountName(), e == null ? null : e.evidence());
	}

	private static void put(Map<String, BigDecimal> others, String kind, Truth truth) {
		if (truth != null) {
			others.put(kind + ":" + others.size(), truth.value());
		}
	}

	/** 틀린 값이 정답과 어떤 관계인지로 원인을 추정한다 */
	static ErrorType classify(BigDecimal truth, BigDecimal extracted, Map<String, BigDecimal> others) {
		if (extracted == null || truth.compareTo(extracted) == 0) {
			return null;
		}
		if (truth.signum() != 0 && truth.negate().compareTo(extracted) == 0) {
			return ErrorType.SIGN;
		}
		if (truth.signum() != 0 && extracted.signum() != 0) {
			// 반올림된 입력(백만원 등)에서도 잡히도록 배율을 log10 으로 비교한다: 천 배 = 3, 백만 배 = 6
			double log = Math.log10(extracted.abs().doubleValue() / truth.abs().doubleValue());
			for (int exponent : List.of(3, 6, 8, -3, -6, -8)) {
				if (Math.abs(log - exponent) < 0.15) {
					return ErrorType.UNIT_SCALE;
				}
			}
		}
		for (Map.Entry<String, BigDecimal> other : others.entrySet()) {
			if (other.getValue().compareTo(extracted) == 0) {
				return other.getKey().startsWith("scope") ? ErrorType.PERIOD_SCOPE_SWAP : ErrorType.FS_DIV_SWAP;
			}
		}
		return ErrorType.VALUE;
	}

	private static List<Bucket> buckets(List<Row> rows, Function<Row, String> key) {
		return rows.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()))
				.entrySet().stream()
				.map(e -> bucket(e.getKey(), e.getValue()))
				.sorted(Comparator.comparingDouble(Bucket::accuracy).thenComparing(Bucket::key))
				.toList();
	}

	private static Bucket bucket(String key, List<Row> rows) {
		int correct = (int) rows.stream().filter(r -> r.outcome() == Outcome.CORRECT).count();
		int wrong = (int) rows.stream().filter(r -> r.outcome() == Outcome.WRONG).count();
		int missing = rows.size() - correct - wrong;
		double accuracy = rows.isEmpty() ? 0 : Math.round(1000.0 * correct / rows.size()) / 10.0;
		return new Bucket(key, rows.size(), correct, wrong, missing, accuracy);
	}

	private Map<String, String> sectionText() {
		return em.createQuery("select s from ReportSection s", ReportSection.class).getResultList().stream()
				.collect(Collectors.groupingBy(s -> s.getReport().getId() + ":" + s.getFsDiv() + ":" + s.getStatementType(),
						Collectors.mapping(ReportSection::getContent, Collectors.joining("\n"))));
	}

	private List<Truth> truths() {
		return em.createQuery("""
				select g.report.id, g.metric.code, g.fsDiv, g.periodScope, g.report.company.corpName,
				       g.report.company.corpCode, g.report.fiscalYear, g.report.reportType, g.metric.standardName,
				       g.metric.statementType, g.value
				from GroundTruth g where g.metric.evaluated = true
				""", Object[].class).getResultList().stream()
				.map(r -> new Truth(new Key((Long) r[0], (String) r[1], (FsDiv) r[2], (PeriodScope) r[3]),
						(String) r[4], (String) r[5], (Integer) r[6], (ReportType) r[7], (String) r[8],
						(StatementType) r[9], (BigDecimal) r[10]))
				.toList();
	}

	private Map<Key, Extracted> extracted(Long runId) {
		return em.createQuery("""
				select r.report.id, r.metric.code, r.fsDiv, r.periodScope, r.normalizedValue, r.rawAccountName,
				       r.evidenceText
				from ExtractionResult r where r.run.id = :runId and r.metric.evaluated = true
				""", Object[].class).setParameter("runId", runId).getResultList().stream()
				.collect(Collectors.toMap(r -> new Key((Long) r[0], (String) r[1], (FsDiv) r[2], (PeriodScope) r[3]),
						r -> new Extracted((BigDecimal) r[4], (String) r[5], (String) r[6]), (a, b) -> a));
	}
}
