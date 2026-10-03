package com.herenas.disclosureai.collect

import com.herenas.disclosureai.dart.DartResponses.Account
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.metric.MetricSpec
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.domain.report.ReportType
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * DART 재무제표 계정 목록 → 항목별 정답 값.
 *
 * 매칭 순서: 표준계정코드(account_id) → 계정명 동의어.
 * 회사가 계정코드를 잘못 붙인 경우가 있어(예: 영업수익에 GrossProfit 코드) 동의어로 한 번 더 찾는다.
 * 자본변동표(SCE)에도 같은 계정이 반복되므로 재무상태표/손익계산서만 본다.
 */
@Component
class GroundTruthMapper {

	data class MappedValue(
		val metricCode: String,
		val periodScope: PeriodScope,
		val value: BigDecimal,
		val accountId: String?,
		val accountName: String?,
	)

	fun map(accounts: List<Account>, metrics: List<MetricSpec>, reportType: ReportType): List<MappedValue> {
		val krw = accounts.filter { it.currency == null || it.currency == "KRW" }
		val result = mutableListOf<MappedValue>()
		for (metric in metrics) {
			if (metric.statementType == StatementType.GENERAL) continue
			val statements = if (metric.statementType == StatementType.BS) BS else IS
			val synonyms = metric.synonyms.map(::normalizeName).toSet()

			val account = find(krw, statements) { metric.dartAccountId != null && metric.dartAccountId == it.accountId }
				?: find(krw, statements) { normalizeName(it.accountNm) in synonyms }
				?: continue

			fun add(scope: PeriodScope, amount: String?) {
				parseAmount(amount)?.let {
					result += MappedValue(metric.code, scope, it, account.accountId, account.accountNm)
				}
			}
			when {
				metric.statementType == StatementType.BS -> add(PeriodScope.INSTANT, account.thstrmAmount)
				reportType == ReportType.ANNUAL -> add(PeriodScope.CUMULATIVE, account.thstrmAmount)
				else -> {
					add(PeriodScope.QUARTER, account.thstrmAmount)
					add(PeriodScope.CUMULATIVE, account.thstrmAddAmount)
				}
			}
		}
		return result
	}

	private fun find(accounts: List<Account>, statements: List<String>, match: (Account) -> Boolean): Account? =
		statements.firstNotNullOfOrNull { statement ->
			accounts.filter { it.sjDiv == statement && match(it) }.minByOrNull { it.ord?.toIntOrNull() ?: Int.MAX_VALUE }
		}

	companion object {
		private val BS = listOf("BS")

		/** 손익계산서가 따로 있으면 IS, 하나로 합친 회사는 CIS 에 있다 */
		private val IS = listOf("IS", "CIS")

		private val LEADING_NUMBER = Regex("^([ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+|\\d+)\\.")

		/** "1,234" → 1234, 빈 값·"-" → null */
		fun parseAmount(amount: String?): BigDecimal? {
			val cleaned = amount?.replace(",", "")?.trim()
			if (cleaned.isNullOrEmpty() || cleaned == "-") return null
			return cleaned.toBigDecimalOrNull()
		}

		/** 공백과 앞쪽 번호(Ⅰ. / 1.)를 지워 비교한다 */
		fun normalizeName(name: String?): String =
			name.orEmpty().replace(Regex("\\s+"), "").replaceFirst(LEADING_NUMBER, "")
	}
}
