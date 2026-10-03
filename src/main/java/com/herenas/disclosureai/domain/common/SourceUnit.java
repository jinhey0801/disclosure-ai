package com.herenas.disclosureai.domain.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 공시 원문에 표기된 단위와 정규화 단위(원/명)로의 배율. */
public enum SourceUnit {
	WON(1),
	THOUSAND_WON(1_000),
	MILLION_WON(1_000_000),
	HUNDRED_MILLION_WON(100_000_000),
	PERSON(1);

	private final BigDecimal multiplier;

	SourceUnit(long multiplier) {
		this.multiplier = BigDecimal.valueOf(multiplier);
	}

	/** 원문 값을 정규화 단위로 변환한다. 원 미만은 반올림. */
	public BigDecimal normalize(BigDecimal rawValue) {
		return rawValue.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
	}
}
