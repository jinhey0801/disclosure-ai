package com.herenas.disclosureai.collect

import com.herenas.disclosureai.domain.report.ReportType
import java.time.LocalDate
import java.time.YearMonth

/**
 * 공시 제목에서 보고서 종류와 기간을 읽는다. 12월 결산 법인만 대상으로 한다.
 * 예: "분기보고서 (2026.03)", "[기재정정]사업보고서 (2025.12)", "반기보고서 (2025.06)"
 */
object ReportTitleParser {

	private val TITLE = Regex("(사업|반기|분기)보고서\\s*\\((\\d{4})\\.(\\d{2})\\)")

	data class ParsedTitle(val reportType: ReportType, val fiscalYear: Int, val periodEnd: LocalDate)

	/** 12월 결산이 아니거나 형식이 다르면 null */
	fun parse(title: String): ParsedTitle? {
		val m = TITLE.find(title) ?: return null
		val (kind, yearText, monthText) = m.destructured
		val year = yearText.toInt()
		val month = monthText.toInt()
		val type = when (kind) {
			"사업" -> ReportType.ANNUAL.takeIf { month == 12 }
			"반기" -> ReportType.HALF.takeIf { month == 6 }
			else -> when (month) {
				3 -> ReportType.Q1
				9 -> ReportType.Q3
				else -> null
			}
		} ?: return null
		return ParsedTitle(type, year, YearMonth.of(year, month).atEndOfMonth())
	}
}
