package com.herenas.disclosureai.validation;

import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.validation.ValidationResult.Outcome;
import com.herenas.disclosureai.domain.validation.ValidationRule.RuleType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 검증 규칙 실행기. 규칙의 종류(RuleType)는 코드, 대상 항목과 임계값은 DB 의 params(JSON)에서 받는다.
 * 값의 출처(정답·추출 결과)를 모르게 만들어 두 곳에 같은 규칙을 쓴다.
 */
@Component
public class ValidationEngine {

	/** (항목, 기간 구분) → 원/명 단위 값. 보고서 하나 + 연결/별도 하나 분량. */
	public record Snapshot(Map<String, BigDecimal> values) {

		public static String key(String metric, PeriodScope scope) {
			return metric + ":" + scope;
		}

		BigDecimal instant(String metric) {
			return values.get(key(metric, PeriodScope.INSTANT));
		}

		/** 재무상태표는 시점 값, 손익은 누적 값으로 비교한다 */
		BigDecimal comparable(String metric) {
			BigDecimal v = values.get(key(metric, PeriodScope.INSTANT));
			return v != null ? v : values.get(key(metric, PeriodScope.CUMULATIVE));
		}
	}

	public record Result(Outcome outcome, String message, Map<String, Object> details) {
	}

	/**
	 * @param prior 전년 동기 같은 보고서 값. 없으면 null (PERIOD_CHANGE 는 SKIPPED)
	 */
	public Result evaluate(RuleType type, Map<String, Object> params, Snapshot current, Snapshot prior) {
		return switch (type) {
			case BALANCE_IDENTITY -> balanceIdentity(params, current);
			case CAPITAL_IMPAIRMENT -> capitalImpairment(params, current);
			case PERIOD_CHANGE -> periodChange(params, current, prior);
		};
	}

	private Result balanceIdentity(Map<String, Object> params, Snapshot s) {
		String totalCode = (String) params.get("total");
		List<String> partCodes = strings(params.get("parts"));
		BigDecimal total = s.instant(totalCode);
		List<BigDecimal> parts = partCodes.stream().map(s::instant).toList();
		if (total == null || parts.contains(null)) {
			return skipped("입력 항목 없음");
		}
		BigDecimal sum = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal diff = total.subtract(sum);
		BigDecimal tolerance = total.abs().multiply(decimal(params.get("toleranceRatio")));
		Map<String, Object> details = Map.of("total", total, "sumOfParts", sum, "difference", diff);
		if (diff.abs().compareTo(tolerance) <= 0) {
			return new Result(Outcome.PASS, diff.signum() == 0 ? "일치" : "허용 오차 이내 차이 " + diff + "원", details);
		}
		return new Result(Outcome.FAIL, "자산총계와 부채+자본 차이 " + diff.toPlainString() + "원", details);
	}

	/** FAIL = 자본잠식 상태 (경고) */
	private Result capitalImpairment(Map<String, Object> params, Snapshot s) {
		BigDecimal equity = s.instant((String) params.get("equity"));
		BigDecimal capital = s.instant((String) params.get("capital"));
		if (equity == null || capital == null || capital.signum() <= 0) {
			return skipped("입력 항목 없음");
		}
		Map<String, Object> details = Map.of("equity", equity, "capital", capital);
		if (equity.signum() < 0) {
			return new Result(Outcome.FAIL, "완전자본잠식 (자본총계 < 0)", details);
		}
		if (equity.compareTo(capital) < 0) {
			BigDecimal ratio = capital.subtract(equity).divide(capital, 4, RoundingMode.HALF_UP);
			return new Result(Outcome.FAIL, "부분자본잠식 (잠식률 " + percent(ratio) + ")",
					Map.of("equity", equity, "capital", capital, "impairmentRatio", ratio));
		}
		return new Result(Outcome.PASS, "자본잠식 아님", details);
	}

	/**
	 * 배수(|당기| / |전기|)가 thresholdFactor 이상이거나 1/thresholdFactor 이하이면 FAIL.
	 * 변동률은 -100% 아래로 내려가지 않아 천분의 일 축소(단위 변환 누락)를 못 잡으므로 배수로 본다.
	 */
	private Result periodChange(Map<String, Object> params, Snapshot current, Snapshot prior) {
		if (prior == null) {
			return skipped("비교할 전년 동기 보고서 없음");
		}
		BigDecimal factorLimit = decimal(params.get("thresholdFactor"));
		List<Map<String, Object>> changes = new ArrayList<>();
		List<String> flagged = new ArrayList<>();
		for (String metric : strings(params.get("metrics"))) {
			BigDecimal now = current.comparable(metric);
			BigDecimal before = prior.comparable(metric);
			if (now == null || before == null || before.signum() == 0 || now.signum() == 0) {
				continue;
			}
			BigDecimal factor = now.abs().divide(before.abs(), MathContext.DECIMAL64).setScale(4, RoundingMode.HALF_UP);
			boolean over = factor.compareTo(factorLimit) >= 0
					|| factor.compareTo(BigDecimal.ONE.divide(factorLimit, MathContext.DECIMAL64)) <= 0;
			Map<String, Object> change = new LinkedHashMap<>();
			change.put("metric", metric);
			change.put("current", now);
			change.put("prior", before);
			change.put("factor", factor);
			change.put("flagged", over);
			changes.add(change);
			if (over) {
				flagged.add(metric + " " + factor.stripTrailingZeros().toPlainString() + "배");
			}
		}
		if (changes.isEmpty()) {
			return skipped("비교 가능한 항목 없음");
		}
		Map<String, Object> details = Map.of("changes", changes);
		if (flagged.isEmpty()) {
			return new Result(Outcome.PASS, "이상 변동 없음", details);
		}
		return new Result(Outcome.FAIL, "전년 동기 대비 이상 변동: " + String.join(", ", flagged), details);
	}

	private static Result skipped(String message) {
		return new Result(Outcome.SKIPPED, message, Map.of());
	}

	private static String percent(BigDecimal ratio) {
		return ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
	}

	private static BigDecimal decimal(Object value) {
		return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
	}

	private static List<String> strings(Object value) {
		return value instanceof List<?> list ? list.stream().map(Object::toString).collect(Collectors.toList())
				: List.of();
	}
}
