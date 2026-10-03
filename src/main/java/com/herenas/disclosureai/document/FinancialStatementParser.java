package com.herenas.disclosureai.document;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.metric.StatementType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * DART 공시 원문(XML)에서 재무상태표·손익계산서만 잘라 표 텍스트로 바꾼다.
 *
 * 위치는 TABLE-GROUP 의 ACLASS({XBRL}BS_C, {XBRL}IS_S1 …)로 찾는다. 목차 제목은 회사마다 달라도 이 값은 일정하다.
 * 셀에 붙은 XBRL 속성(ACODE 계정코드, ACONTEXT 기간)은 사실상의 정답이므로 LLM 입력에는 넣지 않는다.
 * 실제 피투자사 보고자료(PDF·엑셀)에는 이런 태그가 없기 때문이다.
 */
@Component
public class FinancialStatementParser {

	/** BS_C, IS_C1, IS_S2, CIS_C … (C=연결, S=별도) */
	private static final Pattern STATEMENT_CLASS = Pattern.compile("\\{XBRL}(BS|IS|CIS)_([CS])(\\d*)");
	private static final Pattern UNIT = Pattern.compile("\\(\\s*단위\\s*:\\s*([^)]+?)\\s*\\)");
	private static final Set<String> CELL_TAGS = Set.of("th", "td", "te", "tu");

	public record ParsedSection(FsDiv fsDiv, StatementType statementType, int seq, String title, String unitLabel,
			String content, String sourceClass) {
	}

	public List<ParsedSection> parse(String xml) {
		Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
		List<ParsedSection> sections = new ArrayList<>();
		for (Element group : doc.getElementsByTag("TABLE-GROUP")) {
			String aclass = group.attr("ACLASS");
			Matcher m = STATEMENT_CLASS.matcher(aclass);
			if (!m.matches()) {
				continue;
			}
			FsDiv fsDiv = "C".equals(m.group(2)) ? FsDiv.CFS : FsDiv.OFS;
			StatementType type = "BS".equals(m.group(1)) ? StatementType.BS : StatementType.IS;
			int seq = m.group(3).isEmpty() ? 0 : Integer.parseInt(m.group(3));
			Element titleEl = group.getElementsByTag("TITLE").first();
			String title = titleEl == null ? aclass : clean(titleEl.text());
			String content = render(group, title);
			sections.add(new ParsedSection(fsDiv, type, seq, title, unitLabelOf(content), content, aclass));
		}
		return sections;
	}

	/** 본문에서 첫 "(단위 : X)" 의 X. 없으면 null. */
	public static String unitLabelOf(String content) {
		Matcher unit = UNIT.matcher(content);
		return unit.find() ? unit.group(1) : null;
	}

	private String render(Element group, String title) {
		StringBuilder out = new StringBuilder("[").append(title).append("]\n");
		for (Element table : group.getElementsByTag("TABLE")) {
			List<List<String>> grid = toGrid(table);
			if (grid.isEmpty()) {
				continue;
			}
			int headerRows = table.getElementsByTag("THEAD").isEmpty() ? 0
					: table.getElementsByTag("THEAD").first().getElementsByTag("TR").size();
			int columns = grid.stream().mapToInt(List::size).max().orElse(0);
			if (columns <= 1) {
				// 제목·기간·단위 줄로 된 머리 표
				grid.forEach(row -> row.stream().filter(c -> !c.isEmpty()).forEach(c -> out.append(c).append('\n')));
			} else {
				out.append('\n');
				if (headerRows > 0) {
					out.append(row(flattenHeader(grid.subList(0, headerRows), columns))).append('\n');
				}
				grid.subList(headerRows, grid.size()).forEach(r -> out.append(row(r)).append('\n'));
			}
		}
		return out.toString().strip();
	}

	/** rowspan/colspan 을 풀어 행렬로 만든다. 병합된 칸은 같은 값을 반복한다. */
	private List<List<String>> toGrid(Element table) {
		List<List<String>> grid = new ArrayList<>();
		Map<Long, String> carried = new HashMap<>(); // (row << 16 | col) → rowspan 으로 내려온 값
		int r = 0;
		for (Element tr : table.getElementsByTag("TR")) {
			List<String> cells = new ArrayList<>();
			int c = 0;
			for (Element cell : tr.children()) {
				if (!CELL_TAGS.contains(cell.normalName())) {
					continue;
				}
				while (carried.containsKey(key(r, c))) {
					cells.add(carried.remove(key(r, c++)));
				}
				String text = clean(cell.text());
				int colspan = span(cell.attr("COLSPAN"));
				int rowspan = span(cell.attr("ROWSPAN"));
				for (int i = 0; i < colspan; i++, c++) {
					cells.add(text);
					for (int down = 1; down < rowspan; down++) {
						carried.put(key(r + down, c), text);
					}
				}
			}
			while (carried.containsKey(key(r, c))) {
				cells.add(carried.remove(key(r, c++)));
			}
			grid.add(cells);
			r++;
		}
		return grid;
	}

	/** 여러 줄 머리글을 열마다 위에서 아래로 이어 붙인다: "제 11 기 1분기" + "누적" → "제 11 기 1분기 누적" */
	private static List<String> flattenHeader(List<List<String>> headerRows, int columns) {
		List<String> header = new ArrayList<>();
		for (int c = 0; c < columns; c++) {
			Set<String> parts = new LinkedHashSet<>();
			for (List<String> row : headerRows) {
				if (c < row.size() && !row.get(c).isEmpty()) {
					parts.add(row.get(c));
				}
			}
			header.add(parts.isEmpty() && c == 0 ? "과목" : String.join(" ", parts));
		}
		return header;
	}

	private static String row(List<String> cells) {
		return "| " + String.join(" | ", cells) + " |";
	}

	private static String clean(String text) {
		return text.replace('　', ' ').replace(' ', ' ').replaceAll("\\s+", " ").strip();
	}

	private static int span(String value) {
		try {
			return Math.max(1, Integer.parseInt(value.trim()));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	private static long key(int row, int col) {
		return ((long) row << 16) | col;
	}
}
