package com.herenas.disclosureai.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.herenas.disclosureai.collect.GroundTruthMapper.MappedValue;
import com.herenas.disclosureai.collect.GroundTruthMapper.MetricSpec;
import com.herenas.disclosureai.dart.DartResponses.Account;
import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.metric.StatementType;
import com.herenas.disclosureai.domain.report.ReportType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroundTruthMapperTest {

	private final GroundTruthMapper mapper = new GroundTruthMapper();

	private static final MetricSpec TOTAL_EQUITY =
			new MetricSpec("TOTAL_EQUITY", StatementType.BS, "ifrs-full_Equity", Set.of("자본총계"));
	private static final MetricSpec REVENUE =
			new MetricSpec("REVENUE", StatementType.IS, "ifrs-full_Revenue", Set.of("매출액", "영업수익"));
	private static final MetricSpec NET_INCOME =
			new MetricSpec("NET_INCOME", StatementType.IS, "ifrs-full_ProfitLoss", Set.of("당기순이익", "분기순이익(손실)"));

	private static Account account(String sjDiv, String id, String name, String amount, String addAmount, String ord) {
		return new Account("20260821000656", sjDiv, id, name, amount, addAmount, ord, "KRW");
	}

	@Test
	void 자본변동표의_같은_계정은_무시하고_재무상태표_값을_쓴다() {
		List<Account> accounts = List.of(
				account("SCE", "ifrs-full_Equity", "자본총계", "-1015266526", null, "1"),
				account("BS", "ifrs-full_Equity", "자본총계", "50491042363", null, "30"));

		List<MappedValue> result = mapper.map(accounts, List.of(TOTAL_EQUITY), ReportType.Q1);

		assertThat(result).extracting(MappedValue::periodScope, MappedValue::value)
				.containsExactly(tuple(PeriodScope.INSTANT, new BigDecimal("50491042363")));
	}

	@Test
	void 분기보고서_손익은_3개월치와_누적치를_모두_만든다() {
		List<Account> accounts = List.of(
				account("CIS", "ifrs-full_Revenue", "매출액", "30,000", "90,000", "1"));

		List<MappedValue> result = mapper.map(accounts, List.of(REVENUE), ReportType.Q3);

		assertThat(result).extracting(MappedValue::periodScope, MappedValue::value).containsExactly(
				tuple(PeriodScope.QUARTER, new BigDecimal("30000")),
				tuple(PeriodScope.CUMULATIVE, new BigDecimal("90000")));
	}

	@Test
	void 사업보고서_손익은_누적치만_만든다() {
		List<Account> accounts = List.of(
				account("CIS", "ifrs-full_Revenue", "매출액", "92688935704", "", "1"));

		List<MappedValue> result = mapper.map(accounts, List.of(REVENUE), ReportType.ANNUAL);

		assertThat(result).extracting(MappedValue::periodScope).containsExactly(PeriodScope.CUMULATIVE);
	}

	@Test
	void 계정코드가_잘못_붙어도_계정명_동의어로_찾는다() {
		// 알체라: 영업수익에 GrossProfit 코드가 붙어 있음
		List<Account> accounts = List.of(
				account("CIS", "ifrs-full_GrossProfit", "영업수익", "14795533783", "", "1"));

		List<MappedValue> result = mapper.map(accounts, List.of(REVENUE), ReportType.ANNUAL);

		assertThat(result).extracting(MappedValue::value, MappedValue::accountName)
				.containsExactly(tuple(new BigDecimal("14795533783"), "영업수익"));
	}

	@Test
	void 손익계산서가_따로_있으면_포괄손익계산서보다_우선한다() {
		List<Account> accounts = List.of(
				account("CIS", "ifrs-full_ProfitLoss", "분기순이익(손실)", "999", "999", "1"),
				account("IS", "ifrs-full_ProfitLoss", "분기순이익(손실)", "100", "300", "20"));

		List<MappedValue> result = mapper.map(accounts, List.of(NET_INCOME), ReportType.HALF);

		assertThat(result).extracting(MappedValue::value)
				.containsExactly(new BigDecimal("100"), new BigDecimal("300"));
	}

	@Test
	void 금액이_비어_있으면_정답을_만들지_않는다() {
		// 제너셈: 사업보고서 재무상태표 금액이 비어 있음
		List<Account> accounts = List.of(account("BS", "ifrs-full_Equity", "자본총계", "", null, "1"));

		assertThat(mapper.map(accounts, List.of(TOTAL_EQUITY), ReportType.ANNUAL)).isEmpty();
	}

	@Test
	void 계정명_비교는_공백과_앞번호를_무시한다() {
		assertThat(GroundTruthMapper.normalizeName("Ⅰ. 매출 액")).isEqualTo("매출액");
		assertThat(GroundTruthMapper.normalizeName("1.영업수익")).isEqualTo("영업수익");
	}

	@Test
	void 금액_문자열을_숫자로_바꾼다() {
		assertThat(GroundTruthMapper.parseAmount("-1,234")).isEqualByComparingTo("-1234");
		assertThat(GroundTruthMapper.parseAmount("-")).isNull();
		assertThat(GroundTruthMapper.parseAmount(null)).isNull();
	}
}
