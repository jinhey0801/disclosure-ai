package com.herenas.disclosureai.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SourceUnitTest {

	@Test
	void 백만원_단위를_원으로_정규화한다() {
		assertThat(SourceUnit.MILLION_WON.normalize(new BigDecimal("1234.5")))
				.isEqualByComparingTo("1234500000");
	}

	@Test
	void 천원_단위_음수_손실도_정규화한다() {
		assertThat(SourceUnit.THOUSAND_WON.normalize(new BigDecimal("-987")))
				.isEqualByComparingTo("-987000");
	}

	@Test
	void 원_단위는_그대로다() {
		assertThat(SourceUnit.WON.normalize(new BigDecimal("1000")))
				.isEqualByComparingTo("1000");
	}
}
