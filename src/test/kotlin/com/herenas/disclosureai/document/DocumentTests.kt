package com.herenas.disclosureai.document

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.metric.StatementType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FinancialStatementParserTest {

	private val parser = FinancialStatementParser()

	@Test
	fun `재무상태표와 손익계산서만 골라낸다`() {
		assertThat(parser.parse(XML))
			.extracting({ it.fsDiv }, { it.statementType }, { it.seq })
			.containsExactly(tuple(FsDiv.CFS, StatementType.IS, 1), tuple(FsDiv.OFS, StatementType.BS, 0))
	}

	@Test
	fun `병합된 머리글을 열마다 이어 붙인다`() {
		val content = parser.parse(XML)[0].content

		assertThat(content).contains(
			"| 과목 | 제 11 기 1분기 3개월 | 제 11 기 1분기 누적 | 제 10 기 1분기 3개월 | 제 10 기 1분기 누적 |",
		)
		assertThat(content).contains("| 영업수익 | 1,995,528,753 | 1,995,528,753 | 850,589,450 | 850,589,450 |")
		assertThat(content).contains("| 영업이익(손실) | (4,310,000) |")
	}

	@Test
	fun `제목 기간 단위 줄을 남긴다`() {
		val sections = parser.parse(XML)

		assertThat(sections[0].title).isEqualTo("2-2. 연결 포괄손익계산서")
		assertThat(sections[0].content).startsWith("[2-2. 연결 포괄손익계산서]")
			.contains("제 11 기 1분기 2026.01.01 부터 2026.03.31 까지")
			.contains("(단위 : 원)")
		assertThat(sections[0].unitLabel).isEqualTo("원")
		assertThat(sections[1].unitLabel).isEqualTo("천원")
	}

	@Test
	fun `XBRL 속성 계정코드와 영문명은 본문에 남기지 않는다`() {
		assertThat(parser.parse(XML)[0].content)
			.doesNotContain("ifrs-full", "ACODE", "CFY2026", "Gross profit", "Unit : KRW")
	}

	companion object {
		/** 실제 DART 분기보고서 구조를 줄인 것 (알체라 2026.1Q 연결 포괄손익계산서) */
		private val XML = """
			<?xml version="1.0" encoding="utf-8"?>
			<DOCUMENT><BODY>
			<TABLE-GROUP ACLASS="{XBRL}IS_C1">
			<TITLE ATOC="Y" ENG="2-2. Consolidated Statement Of ComprehensiveIncome">2-2. 연결 포괄손익계산서</TITLE>
			<TABLE ACLASS="NORMAL"><TBODY>
			<TR><TE>연결 포괄손익계산서</TE></TR>
			<TR><TE>제 11 기 1분기 2026.01.01 부터 2026.03.31 까지</TE></TR>
			<TR><TE ENG="(Unit : KRW)">(단위 : 원)</TE></TR>
			</TBODY></TABLE>
			<TABLE ACLASS="NORMAL">
			<THEAD>
			<TR><TH ROWSPAN="2">　</TH><TH COLSPAN="2">제 11 기 1분기</TH><TH COLSPAN="2">제 10 기 1분기</TH></TR>
			<TR><TH>3개월</TH><TH>누적</TH><TH>3개월</TH><TH>누적</TH></TR>
			</THEAD>
			<TBODY>
			<TR><TE ENG="Gross profit">영업수익</TE>
			<TE ACODE="ifrs-full_GrossProfit" ACONTEXT="CFY2026dFQQ">1,995,528,753</TE>
			<TE ACODE="ifrs-full_GrossProfit" ACONTEXT="CFY2026dFQA">1,995,528,753</TE>
			<TE ACODE="ifrs-full_GrossProfit" ACONTEXT="PFY2025dFQQ">850,589,450</TE>
			<TE ACODE="ifrs-full_GrossProfit" ACONTEXT="PFY2025dFQA">850,589,450</TE></TR>
			<TR><TE>영업이익(손실)</TE><TE>(4,310,000)</TE><TE>(4,310,000)</TE><TE>(5,210,000)</TE><TE>(5,210,000)</TE></TR>
			</TBODY></TABLE>
			</TABLE-GROUP>
			<TABLE-GROUP ACLASS="{XBRL}EF_C"><TITLE>2-3. 연결 자본변동표</TITLE></TABLE-GROUP>
			<TABLE-GROUP ACLASS="{XBRL}BS_S"><TITLE>4-1. 재무상태표</TITLE>
			<TABLE><TBODY><TR><TE>(단위 : 천원)</TE></TR></TBODY></TABLE>
			<TABLE><THEAD><TR><TH>　</TH><TH>제 11 기 1분기말</TH></TR></THEAD>
			<TBODY><TR><TE>자산총계</TE><TE>35,970,436</TE></TR></TBODY></TABLE>
			</TABLE-GROUP>
			<TABLE-GROUP ACLASS="CB"><TITLE>미상환 전환사채 발행현황</TITLE></TABLE-GROUP>
			</BODY></DOCUMENT>
		""".trimIndent()
	}
}

class CoverageServiceTest {

	private val text = """
		| 영업이익(손실) | (4,310,000) | 12,345,678 |
		| 자산총계 | 1,234,567 |
	""".trimIndent()

	@Test
	fun `음수는 괄호 표기여도 절댓값으로 찾는다`() {
		assertThat(CoverageService.containsNumber(text, BigDecimal("-4310000"))).isTrue()
	}

	@Test
	fun `더 큰 숫자의 일부와는 일치시키지 않는다`() {
		// 2,345,678 은 12,345,678 의 일부지만 다른 숫자다
		assertThat(CoverageService.containsNumber(text, BigDecimal("2345678"))).isFalse()
		assertThat(CoverageService.containsNumber(text, BigDecimal("234567"))).isFalse()
	}

	@Test
	fun `정확히 같은 숫자를 찾는다`() {
		assertThat(CoverageService.containsNumber(text, BigDecimal("1234567"))).isTrue()
	}
}
