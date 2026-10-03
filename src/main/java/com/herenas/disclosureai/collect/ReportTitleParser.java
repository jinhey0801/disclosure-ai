package com.herenas.disclosureai.collect;

import com.herenas.disclosureai.domain.report.ReportType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공시 제목에서 보고서 종류와 기간을 읽는다. 12월 결산 법인만 대상으로 한다.
 * 예: "분기보고서 (2026.03)", "[기재정정]사업보고서 (2025.12)", "반기보고서 (2025.06)"
 */
public final class ReportTitleParser {

	private static final Pattern TITLE = Pattern.compile("(사업|반기|분기)보고서\\s*\\((\\d{4})\\.(\\d{2})\\)");

	private ReportTitleParser() {
	}

	public record ParsedTitle(ReportType reportType, int fiscalYear, LocalDate periodEnd) {
	}

	public static Optional<ParsedTitle> parse(String title) {
		Matcher m = TITLE.matcher(title);
		if (!m.find()) {
			return Optional.empty();
		}
		int year = Integer.parseInt(m.group(2));
		int month = Integer.parseInt(m.group(3));
		ReportType type = switch (m.group(1)) {
			case "사업" -> month == 12 ? ReportType.ANNUAL : null;
			case "반기" -> month == 6 ? ReportType.HALF : null;
			default -> month == 3 ? ReportType.Q1 : month == 9 ? ReportType.Q3 : null;
		};
		if (type == null) {
			return Optional.empty(); // 12월 결산이 아닌 회사
		}
		return Optional.of(new ParsedTitle(type, year, YearMonth.of(year, month).atEndOfMonth()));
	}
}
