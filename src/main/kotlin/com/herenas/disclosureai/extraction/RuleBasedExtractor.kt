package com.herenas.disclosureai.extraction

import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.common.SourceUnit
import com.herenas.disclosureai.domain.metric.MetricSpec
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.domain.report.ReportType
import org.springframework.stereotype.Component

/**
 * LLM 비교용 기준선(baseline). 표 텍스트에서 계정명 동의어로 행을 찾고, 머리글로 열을 고른다.
 *
 * 열 고르기: 머리글의 "제 N 기" 중 가장 큰 N 이 당기. 당기 열 중 "3개월" 은 QUARTER, "누적" 은 CUMULATIVE.
 * 동의어에 없는 계정명이나 머리글이 특이한 표는 못 읽는다. 그 한계가 LLM 이 메워야 할 부분이다.
 */
@Component
class RuleBasedExtractor : Extractor {

	override val name = "rule-based"

	/**
	 * v1: 최초 동의어
	 * v2: 매출액 동의어 "수익" 추가 (V9 마이그레이션) — v1 평가에서 2026 반기 매출액 누락 26건 발견
	 */
	override val version = "v2"

	override fun extract(input: Extractor.Input): List<Extractor.ExtractedValue> =
		input.metrics.filter { it.statementType != StatementType.GENERAL }.flatMap { metric ->
			val synonyms = metric.synonyms.map(::normalizeLabel).toSet()
			input.sectionsOf(metric.statementType).asSequence()
				.map { extractFrom(it, metric, synonyms, input.reportType) }
				.firstOrNull { it.isNotEmpty() }
				.orEmpty()
		}

	private fun extractFrom(
		section: Extractor.Section,
		metric: MetricSpec,
		synonyms: Set<String>,
		reportType: ReportType,
	): List<Extractor.ExtractedValue> {
		val table = TextTable.parse(section.content)
		val row = table.rows.firstOrNull { normalizeLabel(it.label) in synonyms } ?: return emptyList()
		val unit = unitOf(section.unitLabel)
		return pickColumns(table.header, metric.statementType, reportType).mapNotNull { (scope, column) ->
			val raw = row.cells.getOrNull(column) ?: return@mapNotNull null
			val number = TextTable.parseNumber(raw) ?: return@mapNotNull null
			Extractor.ExtractedValue(
				metric.code, scope, row.label, raw, unit, unit?.normalize(number) ?: number, row.line,
			)
		}
	}

	companion object {
		private val PERIOD_NO = Regex("제\\s*(\\d+)\\s*(?:\\(\\s*당\\s*\\)\\s*)?기")
		private val LEADING_ENUM = Regex("^([ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+|\\d+|[가-하])[.)]")
		private val NOTE_REF = Regex("\\(주(석)?[\\d,\\s.]*\\)")

		/** 기간 구분 → 열 번호 */
		internal fun pickColumns(header: List<String>, type: StatementType, reportType: ReportType): Map<PeriodScope, Int> {
			val current = currentColumns(header)
			if (type == StatementType.BS) return mapOf(PeriodScope.INSTANT to current.first())
			if (reportType == ReportType.ANNUAL) return mapOf(PeriodScope.CUMULATIVE to current.first())
			val columns = linkedMapOf<PeriodScope, Int>()
			for (c in current) {
				val h = header[c]
				when {
					"3개월" in h -> columns.putIfAbsent(PeriodScope.QUARTER, c)
					"누적" in h -> columns.putIfAbsent(PeriodScope.CUMULATIVE, c)
				}
			}
			if (columns.isEmpty()) {
				// 3개월/누적 표시가 없는 표: 1분기는 둘이 같고, 그 밖에는 누적으로 본다
				columns[PeriodScope.CUMULATIVE] = current.first()
				if (reportType == ReportType.Q1) columns[PeriodScope.QUARTER] = current.first()
			}
			return columns
		}

		/** "제 N 기" 의 N 이 가장 큰 열들. 머리글이 없거나 기수가 안 보이면 첫 숫자 열. */
		private fun currentColumns(header: List<String>): List<Int> {
			if (header.size < 2) return listOf(1)
			val periods = header.map { h -> PERIOD_NO.find(h)?.groupValues?.get(1)?.toInt() ?: -1 }
			val max = (1 until periods.size).maxOf { periods[it] }
			if (max < 0) {
				val byWord = (1 until header.size).filter { "당" in header[it] }
				return byWord.ifEmpty { listOf(1) }
			}
			return (1 until periods.size).filter { periods[it] == max }
		}

		/** 비교용 계정명: 공백·앞 번호·주석 번호·"(손실)" 제거 */
		internal fun normalizeLabel(label: String): String =
			label.replace(Regex("\\s+"), "")
				.replaceFirst(LEADING_ENUM, "")
				.replace(NOTE_REF, "")
				.replace("(손실)", "")
				.replace("(결손금)", "")

		internal fun unitOf(label: String?): SourceUnit? {
			val s = label?.replace(" ", "") ?: return null
			return when {
				s.startsWith("백만원") -> SourceUnit.MILLION_WON
				s.startsWith("천원") -> SourceUnit.THOUSAND_WON
				s.startsWith("억원") -> SourceUnit.HUNDRED_MILLION_WON
				s.startsWith("원") -> SourceUnit.WON
				else -> null
			}
		}
	}
}
