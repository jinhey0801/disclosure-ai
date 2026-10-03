package com.herenas.disclosureai.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.common.SourceUnit;
import com.herenas.disclosureai.domain.metric.MetricSpec;
import com.herenas.disclosureai.domain.metric.StatementType;
import com.herenas.disclosureai.domain.report.ReportType;
import com.herenas.disclosureai.extraction.Extractor.ExtractedValue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleBasedExtractorTest {

	private final RuleBasedExtractor extractor = new RuleBasedExtractor();

	private static final MetricSpec REVENUE = new MetricSpec("REVENUE", "매출액", StatementType.IS, null,
			Set.of("매출액", "영업수익"));
	private static final MetricSpec OPERATING = new MetricSpec("OPERATING_INCOME", "영업이익", StatementType.IS, null,
			Set.of("영업이익", "영업이익(손실)"));
	private static final MetricSpec ASSETS = new MetricSpec("TOTAL_ASSETS", "자산총계", StatementType.BS, null,
			Set.of("자산총계"));

	/** 실제 본문 형식 (알체라 2026.1Q 연결) */
	private static final String IS_Q3 = """
			[2-2. 연결 포괄손익계산서]
			(단위 : 원)

			| 과목 | 제 11 기 3분기 3개월 | 제 11 기 3분기 누적 | 제 10 기 3분기 3개월 | 제 10 기 3분기 누적 |
			| 영업수익 | 1,995,528,753 | 5,000,000,000 | 850,589,450 | 2,000,000,000 |
			| 영업이익(손실) (주18) | (4,309,495,707) | (9,000,000,000) | (5,205,785,718) | (8,000,000,000) |
			""";

	private static final String BS = """
			[4-1. 재무상태표]
			(단위 : 천원)

			| 과 목 | 제 11 기 3분기말 | 제 10 기말 |
			| 자산 |  |  |
			| Ⅰ. 자산총계 | 33,068,409 | 35,970,436 |
			""";

	private List<ExtractedValue> run(ReportType type, String isContent, String bsContent) {
		return extractor.extract(new Extractor.Input(type, 2026, FsDiv.CFS, List.of(
				new Extractor.Section(StatementType.BS, 0, "재무상태표", "천원", bsContent),
				new Extractor.Section(StatementType.IS, 1, "포괄손익계산서", "원", isContent)),
				List.of(REVENUE, OPERATING, ASSETS)));
	}

	@Test
	void 당기_열에서_3개월과_누적을_구분해_뽑는다() {
		List<ExtractedValue> values = run(ReportType.Q3, IS_Q3, BS);

		assertThat(values).filteredOn(v -> v.metricCode().equals("REVENUE"))
				.extracting(ExtractedValue::periodScope, ExtractedValue::normalizedValue)
				.containsExactlyInAnyOrder(
						tuple(PeriodScope.QUARTER, new BigDecimal("1995528753")),
						tuple(PeriodScope.CUMULATIVE, new BigDecimal("5000000000")));
	}

	@Test
	void 괄호_음수와_주석_번호가_붙은_계정명을_읽는다() {
		List<ExtractedValue> values = run(ReportType.Q3, IS_Q3, BS);

		assertThat(values).filteredOn(v -> v.metricCode().equals("OPERATING_INCOME")
						&& v.periodScope() == PeriodScope.CUMULATIVE)
				.extracting(ExtractedValue::normalizedValue)
				.containsExactly(new BigDecimal("-9000000000"));
	}

	@Test
	void 재무상태표는_당기말_열을_쓰고_단위를_원으로_바꾼다() {
		List<ExtractedValue> values = run(ReportType.Q3, IS_Q3, BS);

		assertThat(values).filteredOn(v -> v.metricCode().equals("TOTAL_ASSETS"))
				.extracting(ExtractedValue::periodScope, ExtractedValue::rawUnit, ExtractedValue::normalizedValue)
				.containsExactly(tuple(PeriodScope.INSTANT, SourceUnit.THOUSAND_WON, new BigDecimal("33068409000")));
	}

	@Test
	void 사업보고서_손익은_누적만_뽑는다() {
		String annual = """
				| 과목 | 제 10 기 | 제 9 기 |
				| 매출액 | 92,688,935,704 | 80,000,000,000 |
				""";
		List<ExtractedValue> values = run(ReportType.ANNUAL, annual, BS);

		assertThat(values).filteredOn(v -> v.metricCode().equals("REVENUE"))
				.extracting(ExtractedValue::periodScope, ExtractedValue::normalizedValue)
				.containsExactly(tuple(PeriodScope.CUMULATIVE, new BigDecimal("92688935704")));
	}

	@Test
	void 동의어에_없는_계정명은_찾지_못한다() {
		String unknown = """
				| 과목 | 제 10 기 |
				| 수익 | 1,000 |
				""";
		assertThat(run(ReportType.ANNUAL, unknown, BS)).noneMatch(v -> v.metricCode().equals("REVENUE"));
	}

	@Test
	void 숫자_표기를_해석한다() {
		assertThat(TextTable.parseNumber("(1,234)")).isEqualByComparingTo("-1234");
		assertThat(TextTable.parseNumber("△1,234")).isEqualByComparingTo("-1234");
		assertThat(TextTable.parseNumber("-")).isEqualByComparingTo("0");
		assertThat(TextTable.parseNumber("")).isNull();
		assertThat(TextTable.parseNumber("주18")).isNull();
	}
}
