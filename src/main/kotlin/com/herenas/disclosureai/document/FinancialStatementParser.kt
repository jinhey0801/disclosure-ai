package com.herenas.disclosureai.document

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.metric.StatementType
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.springframework.stereotype.Component

/**
 * DART 공시 원문(XML)에서 재무상태표·손익계산서만 잘라 표 텍스트로 바꾼다.
 *
 * 위치는 TABLE-GROUP 의 ACLASS({XBRL}BS_C, {XBRL}IS_S1 …)로 찾는다. 목차 제목은 회사마다 달라도 이 값은 일정하다.
 * 셀에 붙은 XBRL 속성(ACODE 계정코드, ACONTEXT 기간)은 사실상의 정답이므로 LLM 입력에는 넣지 않는다.
 * 실제 피투자사 보고자료(PDF·엑셀)에는 이런 태그가 없기 때문이다.
 */
@Component
class FinancialStatementParser {

	data class ParsedSection(
		val fsDiv: FsDiv,
		val statementType: StatementType,
		val seq: Int,
		val title: String,
		val unitLabel: String?,
		val content: String,
		val sourceClass: String,
	)

	fun parse(xml: String): List<ParsedSection> {
		val doc = Jsoup.parse(xml, "", Parser.xmlParser())
		return doc.getElementsByTag("TABLE-GROUP").mapNotNull { group ->
			val aclass = group.attr("ACLASS")
			val m = STATEMENT_CLASS.matchEntire(aclass) ?: return@mapNotNull null
			val (kind, scope, number) = m.destructured
			val title = group.getElementsByTag("TITLE").first()?.let { clean(it.text()) } ?: aclass
			val content = render(group, title)
			ParsedSection(
				fsDiv = if (scope == "C") FsDiv.CFS else FsDiv.OFS,
				statementType = if (kind == "BS") StatementType.BS else StatementType.IS,
				seq = number.toIntOrNull() ?: 0,
				title = title,
				unitLabel = unitLabelOf(content),
				content = content,
				sourceClass = aclass,
			)
		}
	}

	private fun render(group: Element, title: String): String {
		val out = StringBuilder("[").append(title).append("]\n")
		for (table in group.getElementsByTag("TABLE")) {
			val grid = toGrid(table)
			if (grid.isEmpty()) continue
			val headerRows = table.getElementsByTag("THEAD").first()?.getElementsByTag("TR")?.size ?: 0
			val columns = grid.maxOf { it.size }
			if (columns <= 1) {
				// 제목·기간·단위 줄로 된 머리 표
				grid.flatten().filter { it.isNotEmpty() }.forEach { out.append(it).append('\n') }
			} else {
				out.append('\n')
				if (headerRows > 0) {
					out.append(row(flattenHeader(grid.subList(0, headerRows), columns))).append('\n')
				}
				grid.drop(headerRows).forEach { out.append(row(it)).append('\n') }
			}
		}
		return out.toString().trim()
	}

	/** rowspan/colspan 을 풀어 행렬로 만든다. 병합된 칸은 같은 값을 반복한다. */
	private fun toGrid(table: Element): List<List<String>> {
		val carried = mutableMapOf<Pair<Int, Int>, String>() // (행, 열) → rowspan 으로 내려온 값
		return table.getElementsByTag("TR").mapIndexed { r, tr ->
			val cells = mutableListOf<String>()
			var c = 0
			fun fillCarried() {
				while (r to c in carried) {
					cells += carried.getValue(r to c)
					c++
				}
			}
			for (cell in tr.children()) {
				if (cell.normalName() !in CELL_TAGS) continue
				fillCarried()
				val text = clean(cell.text())
				val colspan = span(cell.attr("COLSPAN"))
				val rowspan = span(cell.attr("ROWSPAN"))
				repeat(colspan) {
					cells += text
					for (down in 1 until rowspan) carried[(r + down) to c] = text
					c++
				}
			}
			fillCarried()
			cells
		}
	}

	companion object {
		/** BS_C, IS_C1, IS_S2, CIS_C … (C=연결, S=별도) */
		private val STATEMENT_CLASS = Regex("\\{XBRL}(BS|IS|CIS)_([CS])(\\d*)")
		private val UNIT = Regex("\\(\\s*단위\\s*:\\s*([^)]+?)\\s*\\)")
		private val CELL_TAGS = setOf("th", "td", "te", "tu")

		/** 본문에서 첫 "(단위 : X)" 의 X. 없으면 null. */
		fun unitLabelOf(content: String): String? = UNIT.find(content)?.groupValues?.get(1)

		/** 여러 줄 머리글을 열마다 위에서 아래로 이어 붙인다: "제 11 기 1분기" + "누적" → "제 11 기 1분기 누적" */
		private fun flattenHeader(headerRows: List<List<String>>, columns: Int): List<String> =
			(0 until columns).map { c ->
				val parts = headerRows.mapNotNull { it.getOrNull(c)?.takeIf(String::isNotEmpty) }.distinct()
				if (parts.isEmpty() && c == 0) "과목" else parts.joinToString(" ")
			}

		private fun row(cells: List<String>) = "| " + cells.joinToString(" | ") + " |"

		private fun clean(text: String) =
			text.replace('　', ' ').replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

		private fun span(value: String) = value.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
	}
}
