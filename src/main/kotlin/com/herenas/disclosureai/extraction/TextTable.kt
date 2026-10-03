package com.herenas.disclosureai.extraction

import java.math.BigDecimal

/**
 * FinancialStatementParser 가 만든 "| a | b |" 표 텍스트를 다시 행렬로 읽는다.
 * 머리글은 첫 표 행 중 숫자가 하나도 없는 행으로 본다.
 */
internal data class TextTable(val header: List<String>, val rows: List<Row>) {

	data class Row(val line: String, val cells: List<String>) {
		val label: String get() = cells.firstOrNull().orEmpty()
	}

	companion object {
		private val NUMBER = Regex("^[(△▲-]?\\s*[\\d,]+(\\.\\d+)?\\s*\\)?$")
		private val DIGITS = Regex("\\d+(\\.\\d+)?")

		fun parse(content: String): TextTable {
			var header = emptyList<String>()
			val rows = mutableListOf<Row>()
			for (line in content.lines()) {
				if (!line.startsWith("|")) continue
				val cells = cells(line)
				val hasNumber = cells.drop(1).any { NUMBER.matches(it) }
				if (header.isEmpty() && rows.isEmpty() && !hasNumber) header = cells else rows += Row(line, cells)
			}
			return TextTable(header, rows)
		}

		private fun cells(line: String): List<String> {
			var inner = line.trim().removePrefix("|")
			inner = inner.removeSuffix("|")
			return inner.split("|").map { it.trim() }
		}

		/** 재무제표 숫자 표기 → 숫자. (1,234) / △1,234 / -1,234 는 음수, "-" 하나는 0, 빈칸은 null. */
		fun parseNumber(cell: String?): BigDecimal? {
			var s = cell?.replace(",", "")?.replace(" ", "")?.trim() ?: return null
			if (s.isEmpty()) return null
			if (s == "-") return BigDecimal.ZERO
			var negative = false
			if (s.startsWith("(") && s.endsWith(")")) {
				negative = true
				s = s.substring(1, s.length - 1)
			} else if (s.startsWith("△") || s.startsWith("▲") || s.startsWith("-")) {
				negative = true
				s = s.substring(1)
			}
			if (!DIGITS.matches(s)) return null
			val value = BigDecimal(s)
			return if (negative) value.negate() else value
		}
	}
}
