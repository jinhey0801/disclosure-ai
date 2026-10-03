package com.herenas.disclosureai.dart;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.herenas.disclosureai.dart.DartResponses.Account;
import com.herenas.disclosureai.dart.DartResponses.FinancialStatement;
import com.herenas.disclosureai.dart.DartResponses.Report;
import com.herenas.disclosureai.dart.DartResponses.ReportList;
import com.herenas.disclosureai.domain.common.FsDiv;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class DartClient {

	private static final String OK = "000";
	private static final String NO_DATA = "013";
	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final DartProperties properties;

	public DartClient(RestClient.Builder builder, ObjectMapper objectMapper, DartProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(30));
		this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
		this.objectMapper = objectMapper.copy()
				.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		this.properties = properties;
	}

	/** 정기공시(사업·반기·분기보고서) 목록. 원본과 정정공시를 모두 받는다. */
	public List<Report> periodicReports(String corpCode, LocalDate from, LocalDate to) {
		List<Report> reports = new ArrayList<>();
		int page = 1;
		while (true) {
			int pageNo = page;
			ReportList response = get("/list.json", uri -> uri
					.queryParam("corp_code", corpCode)
					.queryParam("bgn_de", from.format(YYYYMMDD))
					.queryParam("end_de", to.format(YYYYMMDD))
					.queryParam("pblntf_ty", "A")
					.queryParam("page_no", pageNo)
					.queryParam("page_count", 100), ReportList.class);
			if (NO_DATA.equals(response.status())) {
				return reports;
			}
			check(response.status(), response.message());
			reports.addAll(response.list());
			if (response.totalPage() == null || page >= response.totalPage()) {
				return reports;
			}
			page++;
		}
	}

	/** 단일회사 전체 재무제표. 해당 재무제표가 없으면(예: 종속회사가 없어 연결이 없음) 빈 목록. */
	public List<Account> financialStatement(String corpCode, int year, String reprtCode, FsDiv fsDiv) {
		FinancialStatement response = get("/fnlttSinglAcntAll.json", uri -> uri
				.queryParam("corp_code", corpCode)
				.queryParam("bsns_year", year)
				.queryParam("reprt_code", reprtCode)
				.queryParam("fs_div", fsDiv.name()), FinancialStatement.class);
		if (NO_DATA.equals(response.status())) {
			return List.of();
		}
		check(response.status(), response.message());
		return response.list();
	}

	private <T> T get(String path, Function<UriBuilder, UriBuilder> params, Class<T> type) {
		if (!StringUtils.hasText(properties.apiKey())) {
			throw new IllegalStateException("DART_API_KEY 가 설정되지 않았습니다.");
		}
		// DART 는 오류 응답도 200 + JSON(status 필드)으로 주고 Content-Type 이 일정하지 않아 문자열로 받아 직접 파싱한다.
		String body = restClient.get()
				.uri(uri -> params.apply(uri.path(path).queryParam("crtfc_key", properties.apiKey())).build())
				.retrieve()
				.body(String.class);
		try {
			return objectMapper.readValue(body, type);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("DART 응답 파싱 실패: " + path, e);
		}
	}

	private static void check(String status, String message) {
		if (!OK.equals(status)) {
			throw new DartApiException(status, message);
		}
	}
}
