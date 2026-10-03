package com.herenas.disclosureai.validation

import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.validation.ValidationResult.Outcome
import com.herenas.disclosureai.domain.validation.ValidationRule.RuleType
import com.herenas.disclosureai.validation.ValidationEngine.Snapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ValidationEngineTest {

	private val engine = ValidationEngine()

	private val balance = mapOf<String, Any>(
		"total" to "TOTAL_ASSETS", "parts" to listOf("TOTAL_LIABILITIES", "TOTAL_EQUITY"), "toleranceRatio" to 0.001,
	)
	private val impairment = mapOf<String, Any>("equity" to "TOTAL_EQUITY", "capital" to "SHARE_CAPITAL")
	private val change = mapOf<String, Any>("metrics" to listOf("REVENUE", "TOTAL_ASSETS"), "thresholdFactor" to 10)

	private fun bs(assets: Long, liabilities: Long, equity: Long, capital: Long, extra: Map<String, BigDecimal> = emptyMap()) =
		Snapshot(
			mapOf(
				Snapshot.key("TOTAL_ASSETS", PeriodScope.INSTANT) to BigDecimal.valueOf(assets),
				Snapshot.key("TOTAL_LIABILITIES", PeriodScope.INSTANT) to BigDecimal.valueOf(liabilities),
				Snapshot.key("TOTAL_EQUITY", PeriodScope.INSTANT) to BigDecimal.valueOf(equity),
				Snapshot.key("SHARE_CAPITAL", PeriodScope.INSTANT) to BigDecimal.valueOf(capital),
			) + extra,
		)

	@Test
	fun `자산이 부채와 자본의 합이면 통과`() {
		assertThat(engine.evaluate(RuleType.BALANCE_IDENTITY, balance, bs(100, 60, 40, 10), null).outcome)
			.isEqualTo(Outcome.PASS)
	}

	@Test
	fun `원문 반올림 수준의 차이는 통과`() {
		// 폴라리스오피스 2025 1Q: 1원 차이
		val s = bs(525_651_843_284L, 88_566_790_543L, 437_085_052_742L, 1)
		assertThat(engine.evaluate(RuleType.BALANCE_IDENTITY, balance, s, null).outcome).isEqualTo(Outcome.PASS)
	}

	@Test
	fun `차이가 허용 오차를 넘으면 실패`() {
		assertThat(engine.evaluate(RuleType.BALANCE_IDENTITY, balance, bs(100, 60, 30, 10), null).outcome)
			.isEqualTo(Outcome.FAIL)
	}

	@Test
	fun `항목이 빠지면 건너뛴다`() {
		val partial = Snapshot(mapOf(Snapshot.key("TOTAL_ASSETS", PeriodScope.INSTANT) to BigDecimal.TEN))
		assertThat(engine.evaluate(RuleType.BALANCE_IDENTITY, balance, partial, null).outcome).isEqualTo(Outcome.SKIPPED)
	}

	@Test
	fun `자본총계가 자본금보다 작으면 부분자본잠식`() {
		val r = engine.evaluate(RuleType.CAPITAL_IMPAIRMENT, impairment, bs(100, 70, 30, 40), null)
		assertThat(r.outcome).isEqualTo(Outcome.FAIL)
		assertThat(r.message).contains("부분자본잠식", "25.0%")
	}

	@Test
	fun `자본총계가 음수면 완전자본잠식`() {
		assertThat(engine.evaluate(RuleType.CAPITAL_IMPAIRMENT, impairment, bs(100, 110, -10, 40), null).message)
			.contains("완전자본잠식")
	}

	@Test
	fun `전년 동기 대비 10배 이상 변동을 찾는다`() {
		val now = bs(1_500_000, 100, 100, 10, mapOf(Snapshot.key("REVENUE", PeriodScope.CUMULATIVE) to BigDecimal.valueOf(300)))
		val before = bs(1_500, 50, 50, 10, mapOf(Snapshot.key("REVENUE", PeriodScope.CUMULATIVE) to BigDecimal.valueOf(100)))

		val r = engine.evaluate(RuleType.PERIOD_CHANGE, change, now, before)

		// 자산 1000배(단위 오독)는 잡고, 매출 3배(실제 성장 가능)는 넘긴다
		assertThat(r.outcome).isEqualTo(Outcome.FAIL)
		assertThat(r.message).contains("TOTAL_ASSETS 1000배").doesNotContain("REVENUE")
	}

	@Test
	fun `천분의 일로 줄어든 경우도 잡는다`() {
		val r = engine.evaluate(RuleType.PERIOD_CHANGE, change, bs(1_500, 1, 1, 1), bs(1_500_000, 1, 1, 1))

		assertThat(r.outcome).isEqualTo(Outcome.FAIL)
		assertThat(r.message).contains("TOTAL_ASSETS 0.001배")
	}

	@Test
	fun `자산총계가 10억 원 미만이면 단위 오독으로 본다`() {
		val minScale = mapOf<String, Any>("metric" to "TOTAL_ASSETS", "min" to 1_000_000_000L)

		// 백만원 표기를 원으로 읽으면 225억 원이 22,500 원이 된다
		assertThat(engine.evaluate(RuleType.MIN_SCALE, minScale, bs(22_500, 1, 1, 1), null).outcome).isEqualTo(Outcome.FAIL)
		assertThat(engine.evaluate(RuleType.MIN_SCALE, minScale, bs(22_500_000_000L, 1, 1, 1), null).outcome)
			.isEqualTo(Outcome.PASS)
	}

	@Test
	fun `전년 동기 보고서가 없으면 건너뛴다`() {
		assertThat(engine.evaluate(RuleType.PERIOD_CHANGE, change, bs(1, 1, 1, 1), null).outcome).isEqualTo(Outcome.SKIPPED)
	}
}
