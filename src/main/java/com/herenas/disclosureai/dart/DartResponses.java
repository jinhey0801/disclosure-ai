package com.herenas.disclosureai.dart;

import java.util.List;

/** DART Open API 응답. 필드는 snake_case 로 매핑된다 (DartClient 의 ObjectMapper 설정). */
public final class DartResponses {

	private DartResponses() {
	}

	/** 공시검색 list.json */
	public record ReportList(String status, String message, Integer totalPage, List<Report> list) {
	}

	public record Report(String corpCode, String corpName, String reportNm, String rceptNo, String rceptDt) {
	}

	/** 단일회사 전체 재무제표 fnlttSinglAcntAll.json */
	public record FinancialStatement(String status, String message, List<Account> list) {
	}

	/**
	 * 계정 한 줄.
	 * sjDiv: BS(재무상태표) / IS(손익계산서) / CIS(포괄손익계산서) / CF / SCE(자본변동표)
	 * thstrmAmount: 당기 금액 (분기·반기보고서의 손익 항목은 3개월치)
	 * thstrmAddAmount: 당기 누적 금액 (분기·반기보고서의 손익 항목만 존재)
	 */
	public record Account(String rceptNo, String sjDiv, String accountId, String accountNm, String thstrmAmount,
			String thstrmAddAmount, String ord, String currency) {
	}
}
