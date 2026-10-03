package com.herenas.disclosureai.dart

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.readValue
import com.herenas.disclosureai.domain.common.FsDiv
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.springframework.web.util.UriBuilder
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ConfigurationProperties(prefix = "dart")
data class DartProperties(val apiKey: String?, val baseUrl: String)

class DartApiException(status: String?, message: String?) : RuntimeException("DART API error [$status] $message")

/** DART Open API 응답. 필드는 snake_case 로 매핑된다 (DartClient 의 ObjectMapper 설정). */
object DartResponses {

	/** 공시검색 list.json */
	data class ReportList(
		val status: String? = null,
		val message: String? = null,
		val totalPage: Int? = null,
		val list: List<Report> = emptyList(),
	)

	data class Report(
		val corpCode: String,
		val corpName: String,
		val reportNm: String,
		val rceptNo: String,
		val rceptDt: String,
	)

	/** 단일회사 전체 재무제표 fnlttSinglAcntAll.json */
	data class FinancialStatement(
		val status: String? = null,
		val message: String? = null,
		val list: List<Account> = emptyList(),
	)

	/**
	 * 계정 한 줄.
	 * sjDiv: BS(재무상태표) / IS(손익계산서) / CIS(포괄손익계산서) / CF / SCE(자본변동표)
	 * thstrmAmount: 당기 금액 (분기·반기보고서의 손익 항목은 3개월치)
	 * thstrmAddAmount: 당기 누적 금액 (분기·반기보고서의 손익 항목만 존재)
	 */
	data class Account(
		val rceptNo: String? = null,
		val sjDiv: String? = null,
		val accountId: String? = null,
		val accountNm: String? = null,
		val thstrmAmount: String? = null,
		val thstrmAddAmount: String? = null,
		val ord: String? = null,
		val currency: String? = null,
	)
}

@Component
class DartClient(
	builder: RestClient.Builder,
	objectMapper: ObjectMapper,
	private val properties: DartProperties,
) {
	private val restClient: RestClient = builder
		.baseUrl(properties.baseUrl)
		.requestFactory(SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(Duration.ofSeconds(5))
			setReadTimeout(Duration.ofSeconds(30))
		})
		.build()

	private val objectMapper: ObjectMapper = objectMapper.copy()
		.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

	/** 정기공시(사업·반기·분기보고서) 목록. 원본과 정정공시를 모두 받는다. */
	fun periodicReports(corpCode: String, from: LocalDate, to: LocalDate): List<DartResponses.Report> {
		val reports = mutableListOf<DartResponses.Report>()
		var page = 1
		while (true) {
			val pageNo = page
			val response: DartResponses.ReportList = get("/list.json") {
				it.queryParam("corp_code", corpCode)
					.queryParam("bgn_de", from.format(YYYYMMDD))
					.queryParam("end_de", to.format(YYYYMMDD))
					.queryParam("pblntf_ty", "A")
					.queryParam("page_no", pageNo)
					.queryParam("page_count", 100)
			}
			if (response.status == NO_DATA) return reports
			check(response.status, response.message)
			reports += response.list
			if (response.totalPage == null || page >= response.totalPage) return reports
			page++
		}
	}

	/** 단일회사 전체 재무제표. 해당 재무제표가 없으면(예: 종속회사가 없어 연결이 없음) 빈 목록. */
	fun financialStatement(corpCode: String, year: Int, reprtCode: String, fsDiv: FsDiv): List<DartResponses.Account> {
		val response: DartResponses.FinancialStatement = get("/fnlttSinglAcntAll.json") {
			it.queryParam("corp_code", corpCode)
				.queryParam("bsns_year", year)
				.queryParam("reprt_code", reprtCode)
				.queryParam("fs_div", fsDiv.name)
		}
		if (response.status == NO_DATA) return emptyList()
		check(response.status, response.message)
		return response.list
	}

	/** 공시 원문 zip (본문 XML + 첨부 감사보고서 XML 등) */
	fun document(rceptNo: String): ByteArray {
		val apiKey = requireApiKey()
		val body = restClient.get()
			.uri { it.path("/document.xml").queryParam("crtfc_key", apiKey).queryParam("rcept_no", rceptNo).build() }
			.retrieve()
			.body<ByteArray>()
		// 정상이면 zip(PK 로 시작), 오류면 <result><status>…</status><message>…</message></result> XML
		if (body != null && body.size > 2 && body[0] == 'P'.code.toByte() && body[1] == 'K'.code.toByte()) {
			return body
		}
		val text = body?.toString(Charsets.UTF_8).orEmpty()
		throw DartApiException(text.between("<status>", "</status>"), text.between("<message>", "</message>"))
	}

	private fun requireApiKey(): String {
		val key = properties.apiKey
		check(!key.isNullOrBlank()) { "DART_API_KEY 가 설정되지 않았습니다." }
		return key
	}

	private inline fun <reified T : Any> get(path: String, crossinline params: (UriBuilder) -> UriBuilder): T {
		val apiKey = requireApiKey()
		// DART 는 오류 응답도 200 + JSON(status 필드)으로 주고 Content-Type 이 일정하지 않아 문자열로 받아 직접 파싱한다.
		val body = restClient.get()
			.uri { params(it.path(path).queryParam("crtfc_key", apiKey)).build() }
			.retrieve()
			.body<String>()
			?: throw IllegalStateException("DART 응답이 비어 있음: $path")
		return objectMapper.readValue(body)
	}

	private fun check(status: String?, message: String?) {
		if (status != OK) throw DartApiException(status, message)
	}

	private fun String.between(open: String, close: String): String {
		val start = indexOf(open)
		val end = indexOf(close)
		return if (start < 0 || end < start) "?" else substring(start + open.length, end)
	}

	companion object {
		private const val OK = "000"
		private const val NO_DATA = "013"
		private val YYYYMMDD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
	}
}
