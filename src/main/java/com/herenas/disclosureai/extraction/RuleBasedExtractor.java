package com.herenas.disclosureai.extraction;

import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.common.SourceUnit;
import com.herenas.disclosureai.domain.metric.MetricSpec;
import com.herenas.disclosureai.domain.metric.StatementType;
import com.herenas.disclosureai.domain.report.ReportType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * LLM 비교용 기준선(baseline). 표 텍스트에서 계정명 동의어로 행을 찾고, 머리글로 열을 고른다.
 *
 * 열 고르기: 머리글의 "제 N 기" 중 가장 큰 N 이 당기. 당기 열 중 "3개월" 은 QUARTER, "누적" 은 CUMULATIVE.
 * 동의어에 없는 계정명이나 머리글이 특이한 표는 못 읽는다. 그 한계가 LLM 이 메워야 할 부분이다.
 */
@Component
public class RuleBasedExtractor implements Extractor {

	private static final Pattern PERIOD_NO = Pattern.compile("제\\s*(\\d+)\\s*(?:\\(\\s*당\\s*\\)\\s*)?기");
	private static final Pattern LEADING_ENUM = Pattern.compile("^([ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+|\\d+|[가-하])[.)]");
	private static final Pattern NOTE_REF = Pattern.compile("\\(주(석)?[\\d,\\s.]*\\)");

	@Override
	public String name() {
		return "rule-based";
	}

	/**
	 * v1: 최초 동의어
	 * v2: 매출액 동의어 "수익" 추가 (V9 마이그레이션) — v1 평가에서 2026 반기 매출액 누락 26건 발견
	 */
	@Override
	public String version() {
		return "v2";
	}

	@Override
	public List<ExtractedValue> extract(Input input) {
		List<ExtractedValue> values = new ArrayList<>();
		for (MetricSpec metric : input.metrics()) {
			if (metric.statementType() == StatementType.GENERAL) {
				continue;
			}
			Set<String> synonyms = metric.synonyms().stream().map(RuleBasedExtractor::normalizeLabel)
					.collect(Collectors.toSet());
			for (Section section : input.sectionsOf(metric.statementType())) {
				List<ExtractedValue> found = extractFrom(section, metric, synonyms, input.reportType());
				if (!found.isEmpty()) {
					values.addAll(found);
					break;
				}
			}
		}
		return values;
	}

	private List<ExtractedValue> extractFrom(Section section, MetricSpec metric, Set<String> synonyms,
			ReportType reportType) {
		TextTable table = TextTable.parse(section.content());
		TextTable.Row row = table.rows().stream()
				.filter(r -> synonyms.contains(normalizeLabel(r.label())))
				.findFirst().orElse(null);
		if (row == null) {
			return List.of();
		}
		SourceUnit unit = unitOf(section.unitLabel());
		List<ExtractedValue> values = new ArrayList<>();
		pickColumns(table.header(), metric.statementType(), reportType).forEach((scope, column) -> {
			if (column < row.cells().size()) {
				String raw = row.cells().get(column);
				BigDecimal number = TextTable.parseNumber(raw);
				if (number != null) {
					values.add(new ExtractedValue(metric.code(), scope, row.label(), raw, unit,
							unit == null ? number : unit.normalize(number), row.line()));
				}
			}
		});
		return values;
	}

	/** 기간 구분 → 열 번호 */
	static Map<PeriodScope, Integer> pickColumns(List<String> header, StatementType type, ReportType reportType) {
		List<Integer> current = currentColumns(header);
		Map<PeriodScope, Integer> columns = new LinkedHashMap<>();
		if (type == StatementType.BS) {
			columns.put(PeriodScope.INSTANT, current.get(0));
			return columns;
		}
		if (reportType == ReportType.ANNUAL) {
			columns.put(PeriodScope.CUMULATIVE, current.get(0));
			return columns;
		}
		for (int c : current) {
			String h = header.get(c);
			if (h.contains("3개월")) {
				columns.putIfAbsent(PeriodScope.QUARTER, c);
			} else if (h.contains("누적")) {
				columns.putIfAbsent(PeriodScope.CUMULATIVE, c);
			}
		}
		if (columns.isEmpty()) {
			// 3개월/누적 표시가 없는 표: 1분기는 둘이 같고, 그 밖에는 누적으로 본다
			columns.put(PeriodScope.CUMULATIVE, current.get(0));
			if (reportType == ReportType.Q1) {
				columns.put(PeriodScope.QUARTER, current.get(0));
			}
		}
		return columns;
	}

	/** "제 N 기" 의 N 이 가장 큰 열들. 머리글이 없거나 기수가 안 보이면 첫 숫자 열. */
	private static List<Integer> currentColumns(List<String> header) {
		if (header.size() < 2) {
			return List.of(1);
		}
		int[] periods = header.stream().mapToInt(h -> {
			Matcher m = PERIOD_NO.matcher(h);
			return m.find() ? Integer.parseInt(m.group(1)) : -1;
		}).toArray();
		OptionalInt max = IntStream.range(1, periods.length).map(i -> periods[i]).max();
		if (max.isEmpty() || max.getAsInt() < 0) {
			List<Integer> byWord = IntStream.range(1, header.size())
					.filter(i -> header.get(i).contains("당")).boxed().toList();
			return byWord.isEmpty() ? List.of(1) : byWord;
		}
		return IntStream.range(1, periods.length).filter(i -> periods[i] == max.getAsInt()).boxed().toList();
	}

	/** 비교용 계정명: 공백·앞 번호·주석 번호·"(손실)" 제거 */
	static String normalizeLabel(String label) {
		String s = label.replaceAll("\\s+", "");
		s = LEADING_ENUM.matcher(s).replaceFirst("");
		s = NOTE_REF.matcher(s).replaceAll("");
		return s.replace("(손실)", "").replace("(결손금)", "");
	}

	static SourceUnit unitOf(String label) {
		if (label == null) {
			return null;
		}
		String s = label.replace(" ", "");
		if (s.startsWith("백만원")) {
			return SourceUnit.MILLION_WON;
		}
		if (s.startsWith("천원")) {
			return SourceUnit.THOUSAND_WON;
		}
		if (s.startsWith("억원")) {
			return SourceUnit.HUNDRED_MILLION_WON;
		}
		if (s.startsWith("원")) {
			return SourceUnit.WON;
		}
		return null;
	}
}
