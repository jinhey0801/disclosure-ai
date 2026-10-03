package com.herenas.disclosureai.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.herenas.disclosureai.document.FinancialStatementParser.ParsedSection;
import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.metric.StatementType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialStatementParserTest {

	private final FinancialStatementParser parser = new FinancialStatementParser();

	/** 실제 DART 분기보고서 구조를 줄인 것 (알체라 2026.1Q 연결 포괄손익계산서) */
	private static final String XML = """
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
			""";

	@Test
	void 재무상태표와_손익계산서만_골라낸다() {
		List<ParsedSection> sections = parser.parse(XML);

		assertThat(sections).extracting(ParsedSection::fsDiv, ParsedSection::statementType, ParsedSection::seq)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(FsDiv.CFS, StatementType.IS, 1),
						org.assertj.core.groups.Tuple.tuple(FsDiv.OFS, StatementType.BS, 0));
	}

	@Test
	void 병합된_머리글을_열마다_이어_붙인다() {
		String content = parser.parse(XML).get(0).content();

		assertThat(content).contains(
				"| 과목 | 제 11 기 1분기 3개월 | 제 11 기 1분기 누적 | 제 10 기 1분기 3개월 | 제 10 기 1분기 누적 |");
		assertThat(content).contains("| 영업수익 | 1,995,528,753 | 1,995,528,753 | 850,589,450 | 850,589,450 |");
		assertThat(content).contains("| 영업이익(손실) | (4,310,000) |");
	}

	@Test
	void 제목_기간_단위_줄을_남긴다() {
		ParsedSection section = parser.parse(XML).get(0);

		assertThat(section.title()).isEqualTo("2-2. 연결 포괄손익계산서");
		assertThat(section.content()).startsWith("[2-2. 연결 포괄손익계산서]")
				.contains("제 11 기 1분기 2026.01.01 부터 2026.03.31 까지")
				.contains("(단위 : 원)");
		assertThat(section.unitLabel()).isEqualTo("원");
		assertThat(parser.parse(XML).get(1).unitLabel()).isEqualTo("천원");
	}

	@Test
	void XBRL_속성_계정코드와_영문명은_본문에_남기지_않는다() {
		String content = parser.parse(XML).get(0).content();

		assertThat(content).doesNotContain("ifrs-full", "ACODE", "CFY2026", "Gross profit", "Unit : KRW");
	}
}
