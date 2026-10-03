package com.herenas.disclosureai.domain.common

import java.math.BigDecimal
import java.math.RoundingMode

/** 재무제표 구분. 코드값은 DART API(fs_div)와 동일하다. */
enum class FsDiv {
	/** 연결재무제표 */
	CFS,

	/** 별도재무제표 */
	OFS,

	/** 재무제표와 무관한 항목 (임직원 수 등) */
	NONE,
}

/**
 * 값이 가리키는 기간.
 * 손익 항목은 같은 분기보고서 안에 3개월치와 누적치가 함께 있으므로 반드시 구분한다.
 */
enum class PeriodScope {
	/** 시점 값 (재무상태표, 임직원 수) */
	INSTANT,

	/** 해당 분기 3개월 (DART thstrm_amount) */
	QUARTER,

	/** 사업연도 개시일부터 누적 (DART thstrm_add_amount, 사업보고서는 12개월) */
	CUMULATIVE,
}

/** 공시 원문에 표기된 단위와 정규화 단위(원/명)로의 배율. */
enum class SourceUnit(multiplier: Long) {
	WON(1),
	THOUSAND_WON(1_000),
	MILLION_WON(1_000_000),
	HUNDRED_MILLION_WON(100_000_000),
	PERSON(1),
	;

	private val multiplier: BigDecimal = BigDecimal.valueOf(multiplier)

	/** 원문 값을 정규화 단위로 변환한다. 원 미만은 반올림. */
	fun normalize(rawValue: BigDecimal): BigDecimal = rawValue.multiply(multiplier).setScale(0, RoundingMode.HALF_UP)
}
