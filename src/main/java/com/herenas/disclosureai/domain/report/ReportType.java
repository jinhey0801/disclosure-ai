package com.herenas.disclosureai.domain.report;

import java.util.Arrays;

/** 정기공시 보고서 종류. dartCode 는 DART API 의 reprt_code. */
public enum ReportType {
	Q1("11013"),
	HALF("11012"),
	Q3("11014"),
	ANNUAL("11011");

	private final String dartCode;

	ReportType(String dartCode) {
		this.dartCode = dartCode;
	}

	public String dartCode() {
		return dartCode;
	}

	public static ReportType fromDartCode(String dartCode) {
		return Arrays.stream(values())
				.filter(type -> type.dartCode.equals(dartCode))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown reprt_code: " + dartCode));
	}
}
