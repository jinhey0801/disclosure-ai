package com.herenas.disclosureai.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoverageServiceTest {

	private static final String TEXT = """
			| 영업이익(손실) | (4,310,000) | 12,345,678 |
			| 자산총계 | 1,234,567 |
			""";

	@Test
	void 음수는_괄호_표기여도_절댓값으로_찾는다() {
		assertThat(CoverageService.containsNumber(TEXT, new BigDecimal("-4310000"))).isTrue();
	}

	@Test
	void 더_큰_숫자의_일부와는_일치시키지_않는다() {
		// 2,345,678 은 12,345,678 의 일부지만 다른 숫자다
		assertThat(CoverageService.containsNumber(TEXT, new BigDecimal("2345678"))).isFalse();
		assertThat(CoverageService.containsNumber(TEXT, new BigDecimal("234567"))).isFalse();
	}

	@Test
	void 정확히_같은_숫자를_찾는다() {
		assertThat(CoverageService.containsNumber(TEXT, new BigDecimal("1234567"))).isTrue();
	}
}
