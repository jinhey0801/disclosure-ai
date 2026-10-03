package com.herenas.disclosureai.domain.common;

/**
 * 값이 가리키는 기간.
 * 손익 항목은 같은 분기보고서 안에 3개월치와 누적치가 함께 있으므로 반드시 구분한다.
 */
public enum PeriodScope {
	/** 시점 값 (재무상태표, 임직원 수) */
	INSTANT,
	/** 해당 분기 3개월 (DART thstrm_amount) */
	QUARTER,
	/** 사업연도 개시일부터 누적 (DART thstrm_add_amount, 사업보고서는 12개월) */
	CUMULATIVE
}
