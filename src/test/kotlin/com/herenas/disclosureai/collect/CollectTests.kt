package com.herenas.disclosureai.collect

import com.herenas.disclosureai.dart.DartResponses.Account
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.metric.MetricSpec
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.domain.report.ReportType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class GroundTruthMapperTest {

	private val mapper = GroundTruthMapper()

	private val totalEquity = MetricSpec("TOTAL_EQUITY", "자본총계", StatementType.BS, "ifrs-full_Equity", setOf("자본총계"))
	private val revenue = MetricSpec("REVENUE", "매출액", StatementType.IS, "ifrs-full_Revenue", setOf("매출액", "영업수익"))
	private val netIncome = MetricSpec(
		"NET_INCOME", "당기순이익", StatementType.IS, "ifrs-full_ProfitLoss", setOf("당기순이익", "분기순이익(손실)"),
	)

	private fun account(sjDiv: String, id: String, name: String, amount: String?, addAmount: String?, ord: String) =
		Account("20260821000656", sjDiv, id, name, amount, addAmount, ord, "KRW")

	@Test
	fun `자본변동표의 같은 계정은 무시하고 재무상태표 값을 쓴다`() {
		val accounts = listOf(
			account("SCE", "ifrs-full_Equity", "자본총계", "-1015266526", null, "1"),
			account("BS", "ifrs-full_Equity", "자본총계", "50491042363", null, "30"),
		)

		assertThat(mapper.map(accounts, listOf(totalEquity), ReportType.Q1))
			.extracting({ it.periodScope }, { it.value })
			.containsExactly(tuple(PeriodScope.INSTANT, BigDecimal("50491042363")))
	}

	@Test
	fun `분기보고서 손익은 3개월치와 누적치를 모두 만든다`() {
		val accounts = listOf(account("CIS", "ifrs-full_Revenue", "매출액", "30,000", "90,000", "1"))

		assertThat(mapper.map(accounts, listOf(revenue), ReportType.Q3))
			.extracting({ it.periodScope }, { it.value })
			.containsExactly(
				tuple(PeriodScope.QUARTER, BigDecimal("30000")),
				tuple(PeriodScope.CUMULATIVE, BigDecimal("90000")),
			)
	}

	@Test
	fun `사업보고서 손익은 누적치만 만든다`() {
		val accounts = listOf(account("CIS", "ifrs-full_Revenue", "매출액", "92688935704", "", "1"))

		assertThat(mapper.map(accounts, listOf(revenue), ReportType.ANNUAL)).extracting<PeriodScope> { it.periodScope }
			.containsExactly(PeriodScope.CUMULATIVE)
	}

	@Test
	fun `계정코드가 잘못 붙어도 계정명 동의어로 찾는다`() {
		// 알체라: 영업수익에 GrossProfit 코드가 붙어 있음
		val accounts = listOf(account("CIS", "ifrs-full_GrossProfit", "영업수익", "14795533783", "", "1"))

		assertThat(mapper.map(accounts, listOf(revenue), ReportType.ANNUAL))
			.extracting({ it.value }, { it.accountName })
			.containsExactly(tuple(BigDecimal("14795533783"), "영업수익"))
	}

	@Test
	fun `손익계산서가 따로 있으면 포괄손익계산서보다 우선한다`() {
		val accounts = listOf(
			account("CIS", "ifrs-full_ProfitLoss", "분기순이익(손실)", "999", "999", "1"),
			account("IS", "ifrs-full_ProfitLoss", "분기순이익(손실)", "100", "300", "20"),
		)

		assertThat(mapper.map(accounts, listOf(netIncome), ReportType.HALF)).extracting<BigDecimal> { it.value }
			.containsExactly(BigDecimal("100"), BigDecimal("300"))
	}

	@Test
	fun `금액이 비어 있으면 정답을 만들지 않는다`() {
		// 제너셈: 사업보고서 재무상태표 금액이 비어 있음
		val accounts = listOf(account("BS", "ifrs-full_Equity", "자본총계", "", null, "1"))

		assertThat(mapper.map(accounts, listOf(totalEquity), ReportType.ANNUAL)).isEmpty()
	}

	@Test
	fun `계정명 비교는 공백과 앞번호를 무시한다`() {
		assertThat(GroundTruthMapper.normalizeName("Ⅰ. 매출 액")).isEqualTo("매출액")
		assertThat(GroundTruthMapper.normalizeName("1.영업수익")).isEqualTo("영업수익")
	}

	@Test
	fun `금액 문자열을 숫자로 바꾼다`() {
		assertThat(GroundTruthMapper.parseAmount("-1,234")).isEqualByComparingTo("-1234")
		assertThat(GroundTruthMapper.parseAmount("-")).isNull()
		assertThat(GroundTruthMapper.parseAmount(null)).isNull()
	}
}

class ReportSyncChooseTest {

	private fun filing(rceptNo: String, name: String) =
		com.herenas.disclosureai.dart.DartResponses.Report("01405451", "알체라", name, rceptNo, rceptNo.take(8))

	@Test
	fun `원본이 있으면 첨부만 고친 공시는 버린다`() {
		// 알체라 2025 사업보고서: 재무제표는 원본(0318)에 있고 첨부정정(0320)은 감사보고서만 고친 것
		val chosen = ReportSyncService.choose(
			listOf(filing("20260318000741", "사업보고서 (2025.12)"), filing("20260320000325", "[첨부정정]사업보고서 (2025.12)")),
		)
		assertThat(chosen.rceptNo).isEqualTo("20260318000741")
	}

	@Test
	fun `기재정정이 있으면 가장 최신 정정본을 고른다`() {
		val chosen = ReportSyncService.choose(
			listOf(filing("20250514000001", "분기보고서 (2025.03)"), filing("20250825000458", "[기재정정]분기보고서 (2025.03)")),
		)
		assertThat(chosen.rceptNo).isEqualTo("20250825000458")
	}

	@Test
	fun `첨부추가 공시뿐이면 그것을 쓴다`() {
		// 유니온바이오메트릭스 2026 반기: DART 목록에 [첨부추가] 한 건만 있고 본문도 그 안에 있다
		val chosen = ReportSyncService.choose(listOf(filing("20260814002947", "[첨부추가]반기보고서 (2026.06)")))
		assertThat(chosen.rceptNo).isEqualTo("20260814002947")
	}
}

class ReportTitleParserTest {

	@Test
	fun `분기보고서 3월은 1분기`() {
		assertThat(ReportTitleParser.parse("분기보고서 (2026.03)"))
			.isEqualTo(ReportTitleParser.ParsedTitle(ReportType.Q1, 2026, LocalDate.of(2026, 3, 31)))
	}

	@Test
	fun `분기보고서 9월은 3분기`() {
		assertThat(ReportTitleParser.parse("분기보고서 (2025.09)"))
			.isEqualTo(ReportTitleParser.ParsedTitle(ReportType.Q3, 2025, LocalDate.of(2025, 9, 30)))
	}

	@Test
	fun `정정공시 머리말이 있어도 해석한다`() {
		assertThat(ReportTitleParser.parse("[기재정정]사업보고서 (2025.12)"))
			.isEqualTo(ReportTitleParser.ParsedTitle(ReportType.ANNUAL, 2025, LocalDate.of(2025, 12, 31)))
	}

	@Test
	fun `반기보고서`() {
		assertThat(ReportTitleParser.parse("반기보고서 (2025.06)"))
			.isEqualTo(ReportTitleParser.ParsedTitle(ReportType.HALF, 2025, LocalDate.of(2025, 6, 30)))
	}

	@Test
	fun `결산월이 12월이 아니면 건너뛴다`() {
		assertThat(ReportTitleParser.parse("사업보고서 (2025.06)")).isNull()
	}
}
