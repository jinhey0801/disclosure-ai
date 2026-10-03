package com.herenas.disclosureai.extraction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * FinancialStatementParser 가 만든 "| a | b |" 표 텍스트를 다시 행렬로 읽는다.
 * 머리글은 첫 표 행 중 숫자가 하나도 없는 행으로 본다.
 */
record TextTable(List<String> header, List<Row> rows) {

	private static final Pattern NUMBER = Pattern.compile("^[(△▲-]?\\s*[\\d,]+(\\.\\d+)?\\s*\\)?$");

	record Row(String line, List<String> cells) {

		String label() {
			return cells.isEmpty() ? "" : cells.get(0);
		}
	}

	static TextTable parse(String content) {
		List<String> header = List.of();
		List<Row> rows = new ArrayList<>();
		for (String line : content.split("\n")) {
			if (!line.startsWith("|")) {
				continue;
			}
			List<String> cells = cells(line);
			boolean hasNumber = cells.stream().skip(1).anyMatch(c -> NUMBER.matcher(c).matches());
			if (header.isEmpty() && rows.isEmpty() && !hasNumber) {
				header = cells;
			} else {
				rows.add(new Row(line, cells));
			}
		}
		return new TextTable(header, rows);
	}

	private static List<String> cells(String line) {
		String inner = line.strip();
		inner = inner.substring(1, inner.endsWith("|") ? inner.length() - 1 : inner.length());
		return Arrays.stream(inner.split("\\|", -1)).map(String::strip).toList();
	}

	/**
	 * 재무제표 숫자 표기 → 숫자. (1,234) / △1,234 / -1,234 는 음수, "-" 하나는 0, 빈칸은 null.
	 */
	static BigDecimal parseNumber(String cell) {
		if (cell == null) {
			return null;
		}
		String s = cell.replace(",", "").replace(" ", "").strip();
		if (s.isEmpty()) {
			return null;
		}
		if (s.equals("-")) {
			return BigDecimal.ZERO;
		}
		boolean negative = false;
		if (s.startsWith("(") && s.endsWith(")")) {
			negative = true;
			s = s.substring(1, s.length() - 1);
		} else if (s.startsWith("△") || s.startsWith("▲") || s.startsWith("-")) {
			negative = true;
			s = s.substring(1);
		}
		if (!s.matches("\\d+(\\.\\d+)?")) {
			return null;
		}
		BigDecimal value = new BigDecimal(s);
		return negative ? value.negate() : value;
	}
}
