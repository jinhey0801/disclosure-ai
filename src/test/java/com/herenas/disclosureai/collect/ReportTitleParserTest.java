package com.herenas.disclosureai.collect;

import static org.assertj.core.api.Assertions.assertThat;

import com.herenas.disclosureai.collect.ReportTitleParser.ParsedTitle;
import com.herenas.disclosureai.domain.report.ReportType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportTitleParserTest {

	@Test
	void 분기보고서_3월은_1분기() {
		assertThat(ReportTitleParser.parse("분기보고서 (2026.03)"))
				.contains(new ParsedTitle(ReportType.Q1, 2026, LocalDate.of(2026, 3, 31)));
	}

	@Test
	void 분기보고서_9월은_3분기() {
		assertThat(ReportTitleParser.parse("분기보고서 (2025.09)"))
				.contains(new ParsedTitle(ReportType.Q3, 2025, LocalDate.of(2025, 9, 30)));
	}

	@Test
	void 정정공시_머리말이_있어도_해석한다() {
		assertThat(ReportTitleParser.parse("[기재정정]사업보고서 (2025.12)"))
				.contains(new ParsedTitle(ReportType.ANNUAL, 2025, LocalDate.of(2025, 12, 31)));
	}

	@Test
	void 반기보고서() {
		assertThat(ReportTitleParser.parse("반기보고서 (2025.06)"))
				.contains(new ParsedTitle(ReportType.HALF, 2025, LocalDate.of(2025, 6, 30)));
	}

	@Test
	void 결산월이_12월이_아니면_건너뛴다() {
		assertThat(ReportTitleParser.parse("사업보고서 (2025.06)")).isEmpty();
	}
}
