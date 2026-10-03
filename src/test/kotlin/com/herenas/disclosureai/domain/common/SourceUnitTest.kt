package com.herenas.disclosureai.domain.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SourceUnitTest {

	@Test
	fun `백만원 단위를 원으로 정규화한다`() {
		assertThat(SourceUnit.MILLION_WON.normalize(BigDecimal("1234.5"))).isEqualByComparingTo("1234500000")
	}

	@Test
	fun `천원 단위 음수 손실도 정규화한다`() {
		assertThat(SourceUnit.THOUSAND_WON.normalize(BigDecimal("-987"))).isEqualByComparingTo("-987000")
	}

	@Test
	fun `원 단위는 그대로다`() {
		assertThat(SourceUnit.WON.normalize(BigDecimal("1000"))).isEqualByComparingTo("1000")
	}
}
