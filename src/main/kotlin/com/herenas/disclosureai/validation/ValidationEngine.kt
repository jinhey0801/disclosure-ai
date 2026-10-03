package com.herenas.disclosureai.validation

import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.validation.ValidationResult.Outcome
import com.herenas.disclosureai.domain.validation.ValidationRule.RuleType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 검증 규칙 실행기. 규칙의 종류(RuleType)는 코드, 대상 항목과 임계값은 DB 의 params(JSON)에서 받는다.
 * 값의 출처(정답·추출 결과)를 모르게 만들어 두 곳에 같은 규칙을 쓴다.
 */
@Component
class ValidationEngine {

	/** (항목, 기간 구분) → 원/명 단위 값. 보고서 하나 + 연결/별도 하나 분량. */
	data class Snapshot(val values: Map<String, BigDecimal>) {

		fun instant(metric: String): BigDecimal? = values[key(metric, PeriodScope.INSTANT)]

		/** 재무상태표는 시점 값, 손익은 누적 값으로 비교한다 */
		fun comparable(metric: String): BigDecimal? =
			values[key(metric, PeriodScope.INSTANT)] ?: values[key(metric, PeriodScope.CUMULATIVE)]

		companion object {
			fun key(metric: String, scope: PeriodScope) = "$metric:$scope"
		}
	}

	data class Result(val outcome: Outcome, val message: String, val details: Map<String, Any>)

	/** @param prior 전년 동기 같은 보고서 값. 없으면 null (PERIOD_CHANGE 는 SKIPPED) */
	fun evaluate(type: RuleType, params: Map<String, Any>, current: Snapshot, prior: Snapshot?): Result =
		when (type) {
			RuleType.BALANCE_IDENTITY -> balanceIdentity(params, current)
			RuleType.CAPITAL_IMPAIRMENT -> capitalImpairment(params, current)
			RuleType.PERIOD_CHANGE -> periodChange(params, current, prior)
			RuleType.MIN_SCALE -> minScale(params, current)
		}

	private fun balanceIdentity(params: Map<String, Any>, s: Snapshot): Result {
		val total = s.instant(params["total"] as String)
		val parts = strings(params["parts"]).map(s::instant)
		if (total == null || parts.any { it == null }) return skipped("입력 항목 없음")
		val sum = parts.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add)
		val diff = total - sum
		val tolerance = total.abs() * decimal(params["toleranceRatio"])
		val details = mapOf("total" to total, "sumOfParts" to sum, "difference" to diff)
		if (diff.abs() <= tolerance) {
			return Result(Outcome.PASS, if (diff.signum() == 0) "일치" else "허용 오차 이내 차이 ${diff}원", details)
		}
		return Result(Outcome.FAIL, "자산총계와 부채+자본 차이 ${diff.toPlainString()}원", details)
	}

	/** FAIL = 자본잠식 상태 (경고) */
	private fun capitalImpairment(params: Map<String, Any>, s: Snapshot): Result {
		val equity = s.instant(params["equity"] as String)
		val capital = s.instant(params["capital"] as String)
		if (equity == null || capital == null || capital.signum() <= 0) return skipped("입력 항목 없음")
		val details = mapOf("equity" to equity, "capital" to capital)
		if (equity.signum() < 0) return Result(Outcome.FAIL, "완전자본잠식 (자본총계 < 0)", details)
		if (equity < capital) {
			val ratio = (capital - equity).divide(capital, 4, RoundingMode.HALF_UP)
			return Result(Outcome.FAIL, "부분자본잠식 (잠식률 ${percent(ratio)})", details + ("impairmentRatio" to ratio))
		}
		return Result(Outcome.PASS, "자본잠식 아님", details)
	}

	/**
	 * 배수(|당기| / |전기|)가 thresholdFactor 이상이거나 1/thresholdFactor 이하이면 FAIL.
	 * 변동률은 -100% 아래로 내려가지 않아 천분의 일 축소(단위 변환 누락)를 못 잡으므로 배수로 본다.
	 */
	private fun periodChange(params: Map<String, Any>, current: Snapshot, prior: Snapshot?): Result {
		if (prior == null) return skipped("비교할 전년 동기 보고서 없음")
		val limit = decimal(params["thresholdFactor"])
		val lowerLimit = BigDecimal.ONE.divide(limit, MathContext.DECIMAL64)
		val changes = mutableListOf<Map<String, Any>>()
		val flagged = mutableListOf<String>()
		for (metric in strings(params["metrics"])) {
			val now = current.comparable(metric)
			val before = prior.comparable(metric)
			if (now == null || before == null || before.signum() == 0 || now.signum() == 0) continue
			val factor = now.abs().divide(before.abs(), MathContext.DECIMAL64).setScale(4, RoundingMode.HALF_UP)
			val over = factor >= limit || factor <= lowerLimit
			changes += linkedMapOf("metric" to metric, "current" to now, "prior" to before, "factor" to factor, "flagged" to over)
			if (over) flagged += "$metric ${factor.stripTrailingZeros().toPlainString()}배"
		}
		if (changes.isEmpty()) return skipped("비교 가능한 항목 없음")
		val details = mapOf("changes" to changes)
		if (flagged.isEmpty()) return Result(Outcome.PASS, "이상 변동 없음", details)
		return Result(Outcome.FAIL, "전년 동기 대비 이상 변동: ${flagged.joinToString(", ")}", details)
	}

	/** 단위를 통째로 잘못 읽은 경우: 다른 규칙은 비율만 보므로 못 잡는다 */
	private fun minScale(params: Map<String, Any>, s: Snapshot): Result {
		val metric = params["metric"] as String
		val value = s.instant(metric) ?: return skipped("입력 항목 없음")
		val min = decimal(params["min"])
		val details = mapOf("value" to value, "min" to min)
		if (value.abs() < min) {
			return Result(
				Outcome.FAIL,
				"$metric ${value.toPlainString()}원이 최소 규모 ${min.toPlainString()}원보다 작음 (단위 오독 의심)",
				details,
			)
		}
		return Result(Outcome.PASS, "규모 정상", details)
	}

	private fun skipped(message: String) = Result(Outcome.SKIPPED, message, emptyMap())

	private fun percent(ratio: BigDecimal) =
		(ratio * BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%"

	private fun decimal(value: Any?) = value?.let { BigDecimal(it.toString()) } ?: BigDecimal.ZERO

	private fun strings(value: Any?): List<String> = (value as? List<*>)?.map { it.toString() } ?: emptyList()
}
