package com.herenas.disclosureai.extraction

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.common.SourceUnit
import com.herenas.disclosureai.domain.metric.MetricSpec
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.domain.report.ReportType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RuleBasedExtractorTest {

	private val extractor = RuleBasedExtractor()

	private val revenue = MetricSpec("REVENUE", "매출액", StatementType.IS, null, setOf("매출액", "영업수익"))
	private val operating = MetricSpec("OPERATING_INCOME", "영업이익", StatementType.IS, null, setOf("영업이익", "영업이익(손실)"))
	private val assets = MetricSpec("TOTAL_ASSETS", "자산총계", StatementType.BS, null, setOf("자산총계"))

	/** 실제 본문 형식 (알체라 연결 포괄손익계산서를 3분기 형태로) */
	private val isQ3 = """
		[2-2. 연결 포괄손익계산서]
		(단위 : 원)

		| 과목 | 제 11 기 3분기 3개월 | 제 11 기 3분기 누적 | 제 10 기 3분기 3개월 | 제 10 기 3분기 누적 |
		| 영업수익 | 1,995,528,753 | 5,000,000,000 | 850,589,450 | 2,000,000,000 |
		| 영업이익(손실) (주18) | (4,309,495,707) | (9,000,000,000) | (5,205,785,718) | (8,000,000,000) |
	""".trimIndent()

	private val bs = """
		[4-1. 재무상태표]
		(단위 : 천원)

		| 과 목 | 제 11 기 3분기말 | 제 10 기말 |
		| 자산 |  |  |
		| Ⅰ. 자산총계 | 33,068,409 | 35,970,436 |
	""".trimIndent()

	private fun run(type: ReportType, isContent: String, bsContent: String = bs) =
		extractor.extract(
			Extractor.Input(
				type, 2026, FsDiv.CFS,
				listOf(
					Extractor.Section(StatementType.BS, 0, "재무상태표", "천원", bsContent),
					Extractor.Section(StatementType.IS, 1, "포괄손익계산서", "원", isContent),
				),
				listOf(revenue, operating, assets),
			),
		)

	@Test
	fun `당기 열에서 3개월과 누적을 구분해 뽑는다`() {
		assertThat(run(ReportType.Q3, isQ3).filter { it.metricCode == "REVENUE" })
			.extracting({ it.periodScope }, { it.normalizedValue })
			.containsExactlyInAnyOrder(
				tuple(PeriodScope.QUARTER, BigDecimal("1995528753")),
				tuple(PeriodScope.CUMULATIVE, BigDecimal("5000000000")),
			)
	}

	@Test
	fun `괄호 음수와 주석 번호가 붙은 계정명을 읽는다`() {
		assertThat(run(ReportType.Q3, isQ3).filter { it.metricCode == "OPERATING_INCOME" && it.periodScope == PeriodScope.CUMULATIVE })
			.extracting<BigDecimal> { it.normalizedValue }
			.containsExactly(BigDecimal("-9000000000"))
	}

	@Test
	fun `재무상태표는 당기말 열을 쓰고 단위를 원으로 바꾼다`() {
		assertThat(run(ReportType.Q3, isQ3).filter { it.metricCode == "TOTAL_ASSETS" })
			.extracting({ it.periodScope }, { it.rawUnit }, { it.normalizedValue })
			.containsExactly(tuple(PeriodScope.INSTANT, SourceUnit.THOUSAND_WON, BigDecimal("33068409000")))
	}

	@Test
	fun `사업보고서 손익은 누적만 뽑는다`() {
		val annual = """
			| 과목 | 제 10 기 | 제 9 기 |
			| 매출액 | 92,688,935,704 | 80,000,000,000 |
		""".trimIndent()

		assertThat(run(ReportType.ANNUAL, annual).filter { it.metricCode == "REVENUE" })
			.extracting({ it.periodScope }, { it.normalizedValue })
			.containsExactly(tuple(PeriodScope.CUMULATIVE, BigDecimal("92688935704")))
	}

	@Test
	fun `동의어에 없는 계정명은 찾지 못한다`() {
		val unknown = """
			| 과목 | 제 10 기 |
			| 수익 | 1,000 |
		""".trimIndent()

		assertThat(run(ReportType.ANNUAL, unknown)).noneMatch { it.metricCode == "REVENUE" }
	}

	@Test
	fun `숫자 표기를 해석한다`() {
		assertThat(TextTable.parseNumber("(1,234)")).isEqualByComparingTo("-1234")
		assertThat(TextTable.parseNumber("△1,234")).isEqualByComparingTo("-1234")
		assertThat(TextTable.parseNumber("-")).isEqualByComparingTo("0")
		assertThat(TextTable.parseNumber("")).isNull()
		assertThat(TextTable.parseNumber("주18")).isNull()
	}
}

class InputVariantTest {

	private val bs = """
		[4-1. 재무상태표]
		제 11 기 1분기말 2026.03.31 현재
		(단위 : 원)

		| 과목 | 제 11 기 1분기말 | 제 10 기말 |
		| 자산 |  |  |
		| 자산총계 | 33,068,409,968 | 35,970,436,296 |
		| 기타포괄손익누계액 | (77,450,263) | - |
	""".trimIndent()

	private val incomeStatement = """
		[4-2. 포괄손익계산서]
		(단위 : 원)

		| 과목 | 제 11 기 1분기 3개월 | 제 11 기 1분기 누적 |
		| 영업이익(손실) | (4,309,495,707) | (4,309,495,707) |
		| 기본주당이익(손실) (단위 : 원) | (120) | (120) |
	""".trimIndent()

	private fun sections() = listOf(
		Extractor.Section(StatementType.BS, 0, "재무상태표", "원", bs),
		Extractor.Section(StatementType.IS, 1, "포괄손익계산서", "원", incomeStatement),
	)

	@Test
	fun `원문 변형은 그대로다`() {
		assertThat(InputVariant.ORIGINAL.apply(sections())).isEqualTo(sections())
	}

	@Test
	fun `천원으로 바꾸면 숫자를 반올림하고 단위 줄을 고친다`() {
		val converted = InputVariant.THOUSAND_WON.apply(sections())[0]

		assertThat(converted.content).contains(
			"(단위 : 천원)", "| 자산총계 | 33,068,410 | 35,970,436 |", "| 기타포괄손익누계액 | (77,450) | - |",
		)
		assertThat(converted.unitLabel).isEqualTo("천원")
	}

	@Test
	fun `혼합 변형은 재무상태표와 손익의 단위가 다르다`() {
		val mixed = InputVariant.MIXED.apply(sections())

		assertThat(mixed[0].unitLabel).isEqualTo("천원")
		assertThat(mixed[1].unitLabel).isEqualTo("백만원")
		assertThat(mixed[1].content).contains("| 영업이익(손실) | (4,309) | (4,309) |")
	}

	@Test
	fun `주당이익 행은 원 단위로 둔다`() {
		assertThat(InputVariant.MILLION_WON.apply(sections())[1].content)
			.contains("| 기본주당이익(손실) (단위 : 원) | (120) | (120) |")
	}

	@Test
	fun `비표준 단위 표기는 전처리가 단위를 읽지 못한다`() {
		val converted = InputVariant.UNIT_PHRASE.apply(sections())[0]

		assertThat(converted.content).contains("※ 금액 단위: 백만원").doesNotContain("(단위")
		assertThat(converted.unitLabel).isNull()
	}

	@Test
	fun `반올림 허용 오차는 반 단위다`() {
		assertThat(InputVariant.ORIGINAL.tolerance(StatementType.BS)).isEqualByComparingTo("0")
		assertThat(InputVariant.MIXED.tolerance(StatementType.BS)).isEqualByComparingTo("500")
		assertThat(InputVariant.MIXED.tolerance(StatementType.IS)).isEqualByComparingTo("500000")
	}

	@Test
	fun `작은 음수가 0으로 반올림되면 부호를 붙이지 않는다`() {
		assertThat(InputVariant.scaleCell("(400)", BigDecimal.valueOf(1000))).isEqualTo("0")
	}
}
