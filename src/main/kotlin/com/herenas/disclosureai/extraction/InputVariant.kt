package com.herenas.disclosureai.extraction

import com.herenas.disclosureai.document.FinancialStatementParser
import com.herenas.disclosureai.domain.common.SourceUnit
import com.herenas.disclosureai.domain.metric.StatementType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * 같은 공시 본문을 단위만 바꿔 넣는 입력 변형.
 *
 * DART 본문은 모두 원 단위라 "단위가 다름" 함정이 드러나지 않는다. 실제 피투자사 보고자료처럼
 * 천원·백만원, 표마다 다른 단위, 비표준 단위 표기를 만들어 추출기가 단위를 제대로 읽는지 본다.
 * 숫자는 반올림되므로 평가는 tolerance() 이내 차이를 정답으로 본다.
 *
 * @property nonStandardLabel true 면 "(단위 : 백만원)" 대신 "※ 금액 단위: 백만원" 으로 적는다 (전처리의 단위 인식이 실패하는 형태)
 */
enum class InputVariant(
	val label: String,
	private val bsUnit: SourceUnit?,
	private val isUnit: SourceUnit?,
	private val nonStandardLabel: Boolean,
) {
	ORIGINAL("원문 (원)", null, null, false),
	THOUSAND_WON("전체 천원", SourceUnit.THOUSAND_WON, SourceUnit.THOUSAND_WON, false),
	MILLION_WON("전체 백만원", SourceUnit.MILLION_WON, SourceUnit.MILLION_WON, false),
	MIXED("재무상태표 천원 · 손익 백만원", SourceUnit.THOUSAND_WON, SourceUnit.MILLION_WON, false),
	UNIT_PHRASE("백만원 · 비표준 단위 표기", SourceUnit.MILLION_WON, SourceUnit.MILLION_WON, true),
	;

	fun apply(sections: List<Extractor.Section>): List<Extractor.Section> = sections.map(::apply)

	/** 반올림 때문에 생기는 최대 오차 (원). 원문이면 0. */
	fun tolerance(type: StatementType): BigDecimal =
		unitFor(type)?.normalize(BigDecimal.ONE)?.divide(BigDecimal.valueOf(2)) ?: BigDecimal.ZERO

	private fun unitFor(type: StatementType) = if (type == StatementType.BS) bsUnit else isUnit

	private fun apply(section: Extractor.Section): Extractor.Section {
		val unit = unitFor(section.statementType) ?: return section
		val divisor = unit.normalize(BigDecimal.ONE)
		val unitName = if (unit == SourceUnit.THOUSAND_WON) "천원" else "백만원"
		val content = section.content.lines().joinToString("\n") { line ->
			if (line.startsWith("|")) {
				scaleRow(line, divisor)
			} else {
				WON_UNIT_LINE.replace(line, if (nonStandardLabel) "※ 금액 단위: $unitName" else "(단위 : $unitName)")
			}
		}
		// 전처리와 같은 규칙으로 단위를 다시 읽는다 (비표준 표기면 null)
		return section.copy(unitLabel = FinancialStatementParser.unitLabelOf(content), content = content)
	}

	companion object {
		private val WON_UNIT_LINE = Regex("\\(\\s*단위\\s*:\\s*원\\s*\\)")

		/** 첫 칸(계정명)은 두고 숫자 칸만 바꾼다. 주당이익은 원 단위로 남는 게 보통이라 그대로 둔다. */
		private fun scaleRow(line: String, divisor: BigDecimal): String {
			val row = TextTable.parse(line).rows.firstOrNull()
			if (row == null || "주당" in row.label) return line
			return row.cells.drop(1).joinToString(" | ", prefix = "| ${row.label} | ", postfix = " |") {
				scaleCell(it, divisor)
			}
		}

		internal fun scaleCell(cell: String, divisor: BigDecimal): String {
			val value = TextTable.parseNumber(cell)
			if (value == null || value.signum() == 0) return cell
			val scaled = value.abs().divide(divisor, 0, RoundingMode.HALF_UP)
			val digits = DecimalFormat("#,##0").format(scaled)
			if (value.signum() > 0 || scaled.signum() == 0) return digits
			val trimmed = cell.trim()
			return when {
				trimmed.startsWith("(") -> "($digits)"
				trimmed.startsWith("△") -> "△$digits"
				else -> "-$digits"
			}
		}
	}
}
