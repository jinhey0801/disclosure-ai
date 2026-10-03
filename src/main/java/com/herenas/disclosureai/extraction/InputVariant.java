package com.herenas.disclosureai.extraction;

import com.herenas.disclosureai.document.FinancialStatementParser;
import com.herenas.disclosureai.domain.common.SourceUnit;
import com.herenas.disclosureai.domain.metric.StatementType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 같은 공시 본문을 단위만 바꿔 넣는 입력 변형.
 *
 * DART 본문은 모두 원 단위라 "단위가 다름" 함정이 드러나지 않는다. 실제 피투자사 보고자료처럼
 * 천원·백만원, 표마다 다른 단위, 비표준 단위 표기를 만들어 추출기가 단위를 제대로 읽는지 본다.
 * 숫자는 반올림되므로 평가는 tolerance() 이내 차이를 정답으로 본다.
 */
public enum InputVariant {

	ORIGINAL("원문 (원)", null, null, false),
	THOUSAND_WON("전체 천원", SourceUnit.THOUSAND_WON, SourceUnit.THOUSAND_WON, false),
	MILLION_WON("전체 백만원", SourceUnit.MILLION_WON, SourceUnit.MILLION_WON, false),
	MIXED("재무상태표 천원 · 손익 백만원", SourceUnit.THOUSAND_WON, SourceUnit.MILLION_WON, false),
	UNIT_PHRASE("백만원 · 비표준 단위 표기", SourceUnit.MILLION_WON, SourceUnit.MILLION_WON, true);

	private static final Pattern WON_UNIT_LINE = Pattern.compile("\\(\\s*단위\\s*:\\s*원\\s*\\)");
	private static final BigDecimal TWO = BigDecimal.valueOf(2);

	private final String label;
	private final SourceUnit bsUnit;
	private final SourceUnit isUnit;
	/** true 면 "(단위 : 백만원)" 대신 "※ 금액 단위: 백만원" 으로 적는다 (전처리의 단위 인식이 실패하는 형태) */
	private final boolean nonStandardLabel;

	InputVariant(String label, SourceUnit bsUnit, SourceUnit isUnit, boolean nonStandardLabel) {
		this.label = label;
		this.bsUnit = bsUnit;
		this.isUnit = isUnit;
		this.nonStandardLabel = nonStandardLabel;
	}

	public String label() {
		return label;
	}

	public List<Extractor.Section> apply(List<Extractor.Section> sections) {
		return sections.stream().map(this::apply).toList();
	}

	/** 반올림 때문에 생기는 최대 오차 (원). 원문이면 0. */
	public BigDecimal tolerance(StatementType type) {
		SourceUnit unit = unitFor(type);
		return unit == null ? BigDecimal.ZERO : unit.normalize(BigDecimal.ONE).divide(TWO);
	}

	private SourceUnit unitFor(StatementType type) {
		return type == StatementType.BS ? bsUnit : isUnit;
	}

	private Extractor.Section apply(Extractor.Section section) {
		SourceUnit unit = unitFor(section.statementType());
		if (unit == null) {
			return section;
		}
		BigDecimal divisor = unit.normalize(BigDecimal.ONE);
		String unitName = unit == SourceUnit.THOUSAND_WON ? "천원" : "백만원";
		String content = section.content().lines()
				.map(line -> line.startsWith("|") ? scaleRow(line, divisor)
						: WON_UNIT_LINE.matcher(line).replaceAll(nonStandardLabel
								? "※ 금액 단위: " + unitName : "(단위 : " + unitName + ")"))
				.collect(Collectors.joining("\n"));
		// 전처리와 같은 규칙으로 단위를 다시 읽는다 (비표준 표기면 null)
		return new Extractor.Section(section.statementType(), section.seq(), section.title(),
				FinancialStatementParser.unitLabelOf(content), content);
	}

	/** 첫 칸(계정명)은 두고 숫자 칸만 바꾼다. 주당이익은 원 단위로 남는 게 보통이라 그대로 둔다. */
	private static String scaleRow(String line, BigDecimal divisor) {
		TextTable.Row row = TextTable.parse(line).rows().stream().findFirst().orElse(null);
		if (row == null || row.label().contains("주당")) {
			return line;
		}
		StringBuilder out = new StringBuilder("| ").append(row.label());
		for (String cell : row.cells().subList(1, row.cells().size())) {
			out.append(" | ").append(scaleCell(cell, divisor));
		}
		return out.append(" |").toString();
	}

	static String scaleCell(String cell, BigDecimal divisor) {
		BigDecimal value = TextTable.parseNumber(cell);
		if (value == null || value.signum() == 0) {
			return cell;
		}
		BigDecimal scaled = value.abs().divide(divisor, 0, RoundingMode.HALF_UP);
		String digits = new DecimalFormat("#,##0").format(scaled);
		if (value.signum() > 0 || scaled.signum() == 0) {
			return digits;
		}
		String trimmed = cell.strip();
		if (trimmed.startsWith("(")) {
			return "(" + digits + ")";
		}
		return (trimmed.startsWith("△") ? "△" : "-") + digits;
	}
}
