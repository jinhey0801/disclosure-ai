package com.herenas.disclosureai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.validation.ValidationResult.Outcome;
import com.herenas.disclosureai.domain.validation.ValidationRule.RuleType;
import com.herenas.disclosureai.validation.ValidationEngine.Snapshot;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationEngineTest {

	private final ValidationEngine engine = new ValidationEngine();

	private static final Map<String, Object> BALANCE = Map.of("total", "TOTAL_ASSETS",
			"parts", List.of("TOTAL_LIABILITIES", "TOTAL_EQUITY"), "toleranceRatio", 0.001);
	private static final Map<String, Object> IMPAIRMENT = Map.of("equity", "TOTAL_EQUITY", "capital", "SHARE_CAPITAL");
	private static final Map<String, Object> CHANGE = Map.of("metrics", List.of("REVENUE", "TOTAL_ASSETS"),
			"thresholdFactor", 10);

	private static Snapshot bs(long assets, long liabilities, long equity, long capital) {
		Map<String, BigDecimal> v = new HashMap<>();
		v.put(Snapshot.key("TOTAL_ASSETS", PeriodScope.INSTANT), BigDecimal.valueOf(assets));
		v.put(Snapshot.key("TOTAL_LIABILITIES", PeriodScope.INSTANT), BigDecimal.valueOf(liabilities));
		v.put(Snapshot.key("TOTAL_EQUITY", PeriodScope.INSTANT), BigDecimal.valueOf(equity));
		v.put(Snapshot.key("SHARE_CAPITAL", PeriodScope.INSTANT), BigDecimal.valueOf(capital));
		return new Snapshot(v);
	}

	@Test
	void 자산이_부채와_자본의_합이면_통과() {
		assertThat(engine.evaluate(RuleType.BALANCE_IDENTITY, BALANCE, bs(100, 60, 40, 10), null).outcome())
				.isEqualTo(Outcome.PASS);
	}

	@Test
	void 원문_반올림_수준의_차이는_통과() {
		// 폴라리스오피스 2025 1Q: 1원 차이
		assertThat(engine.evaluate(RuleType.BALANCE_IDENTITY, BALANCE,
				bs(525_651_843_284L, 88_566_790_543L, 437_085_052_742L, 1), null).outcome())
				.isEqualTo(Outcome.PASS);
	}

	@Test
	void 차이가_허용_오차를_넘으면_실패() {
		assertThat(engine.evaluate(RuleType.BALANCE_IDENTITY, BALANCE, bs(100, 60, 30, 10), null).outcome())
				.isEqualTo(Outcome.FAIL);
	}

	@Test
	void 항목이_빠지면_건너뛴다() {
		Snapshot partial = new Snapshot(Map.of(Snapshot.key("TOTAL_ASSETS", PeriodScope.INSTANT), BigDecimal.TEN));
		assertThat(engine.evaluate(RuleType.BALANCE_IDENTITY, BALANCE, partial, null).outcome())
				.isEqualTo(Outcome.SKIPPED);
	}

	@Test
	void 자본총계가_자본금보다_작으면_부분자본잠식() {
		ValidationEngine.Result r = engine.evaluate(RuleType.CAPITAL_IMPAIRMENT, IMPAIRMENT, bs(100, 70, 30, 40), null);
		assertThat(r.outcome()).isEqualTo(Outcome.FAIL);
		assertThat(r.message()).contains("부분자본잠식", "25.0%");
	}

	@Test
	void 자본총계가_음수면_완전자본잠식() {
		ValidationEngine.Result r = engine.evaluate(RuleType.CAPITAL_IMPAIRMENT, IMPAIRMENT, bs(100, 110, -10, 40), null);
		assertThat(r.message()).contains("완전자본잠식");
	}

	@Test
	void 전년_동기_대비_10배_이상_변동을_찾는다() {
		Map<String, BigDecimal> now = new HashMap<>(bs(1_500_000, 100, 100, 10).values());
		now.put(Snapshot.key("REVENUE", PeriodScope.CUMULATIVE), BigDecimal.valueOf(300));
		Map<String, BigDecimal> before = new HashMap<>(bs(1_500, 50, 50, 10).values());
		before.put(Snapshot.key("REVENUE", PeriodScope.CUMULATIVE), BigDecimal.valueOf(100));

		ValidationEngine.Result r = engine.evaluate(RuleType.PERIOD_CHANGE, CHANGE, new Snapshot(now),
				new Snapshot(before));

		// 자산 1000배(단위 오독)는 잡고, 매출 3배(실제 성장 가능)는 넘긴다
		assertThat(r.outcome()).isEqualTo(Outcome.FAIL);
		assertThat(r.message()).contains("TOTAL_ASSETS 1000배").doesNotContain("REVENUE");
	}

	@Test
	void 천분의_일로_줄어든_경우도_잡는다() {
		ValidationEngine.Result r = engine.evaluate(RuleType.PERIOD_CHANGE, CHANGE, bs(1_500, 1, 1, 1),
				bs(1_500_000, 1, 1, 1));

		assertThat(r.outcome()).isEqualTo(Outcome.FAIL);
		assertThat(r.message()).contains("TOTAL_ASSETS 0.001배");
	}

	@Test
	void 자산총계가_10억_원_미만이면_단위_오독으로_본다() {
		Map<String, Object> minScale = Map.of("metric", "TOTAL_ASSETS", "min", 1_000_000_000L);

		// 백만원 표기를 원으로 읽으면 225억 원이 22,500 원이 된다
		assertThat(engine.evaluate(RuleType.MIN_SCALE, minScale, bs(22_500, 1, 1, 1), null).outcome())
				.isEqualTo(Outcome.FAIL);
		assertThat(engine.evaluate(RuleType.MIN_SCALE, minScale, bs(22_500_000_000L, 1, 1, 1), null).outcome())
				.isEqualTo(Outcome.PASS);
	}

	@Test
	void 전년_동기_보고서가_없으면_건너뛴다() {
		assertThat(engine.evaluate(RuleType.PERIOD_CHANGE, CHANGE, bs(1, 1, 1, 1), null).outcome())
				.isEqualTo(Outcome.SKIPPED);
	}
}
