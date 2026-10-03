package com.herenas.disclosureai.collect;

import com.herenas.disclosureai.dart.DartResponses.Account;
import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.metric.MetricSpec;
import com.herenas.disclosureai.domain.metric.StatementType;
import com.herenas.disclosureai.domain.report.ReportType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * DART 재무제표 계정 목록 → 항목별 정답 값.
 *
 * 매칭 순서: 표준계정코드(account_id) → 계정명 동의어.
 * 회사가 계정코드를 잘못 붙인 경우가 있어(예: 영업수익에 GrossProfit 코드) 동의어로 한 번 더 찾는다.
 * 자본변동표(SCE)에도 같은 계정이 반복되므로 재무상태표/손익계산서만 본다.
 */
@Component
public class GroundTruthMapper {

	private static final List<String> BS = List.of("BS");
	/** 손익계산서가 따로 있으면 IS, 하나로 합친 회사는 CIS 에 있다 */
	private static final List<String> IS = List.of("IS", "CIS");

	public record MappedValue(String metricCode, PeriodScope periodScope, BigDecimal value, String accountId,
			String accountName) {
	}

	public List<MappedValue> map(List<Account> accounts, List<MetricSpec> metrics, ReportType reportType) {
		List<Account> krw = accounts.stream()
				.filter(a -> a.currency() == null || "KRW".equals(a.currency()))
				.toList();
		List<MappedValue> result = new ArrayList<>();
		for (MetricSpec metric : metrics) {
			if (metric.statementType() == StatementType.GENERAL) {
				continue;
			}
			List<String> statements = metric.statementType() == StatementType.BS ? BS : IS;
			Set<String> synonyms = metric.synonyms().stream()
					.map(GroundTruthMapper::normalizeName)
					.collect(Collectors.toSet());

			Optional<Account> account = find(krw, statements,
					a -> metric.dartAccountId() != null && metric.dartAccountId().equals(a.accountId()))
					.or(() -> find(krw, statements, a -> synonyms.contains(normalizeName(a.accountNm()))));
			if (account.isEmpty()) {
				continue;
			}
			Account a = account.get();
			if (metric.statementType() == StatementType.BS) {
				add(result, metric, PeriodScope.INSTANT, a.thstrmAmount(), a);
			} else if (reportType == ReportType.ANNUAL) {
				add(result, metric, PeriodScope.CUMULATIVE, a.thstrmAmount(), a);
			} else {
				add(result, metric, PeriodScope.QUARTER, a.thstrmAmount(), a);
				add(result, metric, PeriodScope.CUMULATIVE, a.thstrmAddAmount(), a);
			}
		}
		return result;
	}

	private static Optional<Account> find(List<Account> accounts, List<String> statements, Predicate<Account> match) {
		for (String statement : statements) {
			Optional<Account> found = accounts.stream()
					.filter(a -> statement.equals(a.sjDiv()))
					.filter(match)
					.min(Comparator.comparingInt(a -> parseOrd(a.ord())));
			if (found.isPresent()) {
				return found;
			}
		}
		return Optional.empty();
	}

	private static void add(List<MappedValue> result, MetricSpec metric, PeriodScope scope, String amount,
			Account account) {
		BigDecimal value = parseAmount(amount);
		if (value != null) {
			result.add(new MappedValue(metric.code(), scope, value, account.accountId(), account.accountNm()));
		}
	}

	/** "1,234" → 1234, 빈 값·"-" → null */
	static BigDecimal parseAmount(String amount) {
		if (amount == null) {
			return null;
		}
		String cleaned = amount.replace(",", "").trim();
		if (cleaned.isEmpty() || "-".equals(cleaned)) {
			return null;
		}
		try {
			return new BigDecimal(cleaned);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 공백과 앞쪽 번호(Ⅰ. / 1.)를 지워 비교한다 */
	static String normalizeName(String name) {
		if (name == null) {
			return "";
		}
		return name.replaceAll("\\s+", "").replaceFirst("^([ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+|\\d+)\\.", "");
	}

	private static int parseOrd(String ord) {
		try {
			return Integer.parseInt(ord);
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}
}
