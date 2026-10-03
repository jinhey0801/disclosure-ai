package com.herenas.disclosureai.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.herenas.disclosureai.domain.metric.StatementType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputVariantTest {

	private static final String BS = """
			[4-1. 재무상태표]
			제 11 기 1분기말 2026.03.31 현재
			(단위 : 원)

			| 과목 | 제 11 기 1분기말 | 제 10 기말 |
			| 자산 |  |  |
			| 자산총계 | 33,068,409,968 | 35,970,436,296 |
			| 기타포괄손익누계액 | (77,450,263) | - |
			""";

	private static final String IS = """
			[4-2. 포괄손익계산서]
			(단위 : 원)

			| 과목 | 제 11 기 1분기 3개월 | 제 11 기 1분기 누적 |
			| 영업이익(손실) | (4,309,495,707) | (4,309,495,707) |
			| 기본주당이익(손실) (단위 : 원) | (120) | (120) |
			""";

	private static List<Extractor.Section> sections() {
		return List.of(new Extractor.Section(StatementType.BS, 0, "재무상태표", "원", BS),
				new Extractor.Section(StatementType.IS, 1, "포괄손익계산서", "원", IS));
	}

	@Test
	void 원문_변형은_그대로다() {
		assertThat(InputVariant.ORIGINAL.apply(sections())).isEqualTo(sections());
	}

	@Test
	void 천원으로_바꾸면_숫자를_반올림하고_단위_줄을_고친다() {
		Extractor.Section bs = InputVariant.THOUSAND_WON.apply(sections()).get(0);

		assertThat(bs.content()).contains("(단위 : 천원)", "| 자산총계 | 33,068,410 | 35,970,436 |",
				"| 기타포괄손익누계액 | (77,450) | - |");
		assertThat(bs.unitLabel()).isEqualTo("천원");
	}

	@Test
	void 혼합_변형은_재무상태표와_손익의_단위가_다르다() {
		List<Extractor.Section> mixed = InputVariant.MIXED.apply(sections());

		assertThat(mixed.get(0).unitLabel()).isEqualTo("천원");
		assertThat(mixed.get(1).unitLabel()).isEqualTo("백만원");
		assertThat(mixed.get(1).content()).contains("| 영업이익(손실) | (4,309) | (4,309) |");
	}

	@Test
	void 주당이익_행은_원_단위로_둔다() {
		Extractor.Section is = InputVariant.MILLION_WON.apply(sections()).get(1);

		assertThat(is.content()).contains("| 기본주당이익(손실) (단위 : 원) | (120) | (120) |");
	}

	@Test
	void 비표준_단위_표기는_전처리가_단위를_읽지_못한다() {
		Extractor.Section bs = InputVariant.UNIT_PHRASE.apply(sections()).get(0);

		assertThat(bs.content()).contains("※ 금액 단위: 백만원").doesNotContain("(단위");
		assertThat(bs.unitLabel()).isNull();
	}

	@Test
	void 반올림_허용_오차는_반_단위다() {
		assertThat(InputVariant.ORIGINAL.tolerance(StatementType.BS)).isEqualByComparingTo("0");
		assertThat(InputVariant.MIXED.tolerance(StatementType.BS)).isEqualByComparingTo("500");
		assertThat(InputVariant.MIXED.tolerance(StatementType.IS)).isEqualByComparingTo("500000");
	}

	@Test
	void 작은_음수가_0으로_반올림되면_부호를_붙이지_않는다() {
		assertThat(InputVariant.scaleCell("(400)", BigDecimal.valueOf(1000))).isEqualTo("0");
	}
}
