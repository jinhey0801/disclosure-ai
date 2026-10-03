package com.herenas.disclosureai.extraction

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.common.PeriodScope
import com.herenas.disclosureai.domain.common.SourceUnit
import com.herenas.disclosureai.domain.metric.MetricSpec
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.domain.report.ReportType
import java.math.BigDecimal

/**
 * 재무제표 본문 → 항목 값. 규칙 기반·LLM 등 구현을 바꿔 끼워도 배치·검증·평가는 그대로 쓴다.
 * 호출 단위는 (보고서 하나, 연결/별도 하나).
 */
interface Extractor {

	/** extraction_run.model 에 기록된다 (예: rule-based, claude-…) */
	val name: String

	/** extraction_run.prompt_version 에 기록된다 (규칙·프롬프트를 바꾸면 올린다) */
	val version: String

	fun extract(input: Input): List<ExtractedValue>

	data class Input(
		val reportType: ReportType,
		val fiscalYear: Int,
		val fsDiv: FsDiv,
		val sections: List<Section>,
		val metrics: List<MetricSpec>,
	) {
		fun sectionsOf(type: StatementType) = sections.filter { it.statementType == type }
	}

	data class Section(
		val statementType: StatementType,
		val seq: Int,
		val title: String,
		val unitLabel: String?,
		val content: String,
	)

	/**
	 * @property normalizedValue 원/명 단위. 찾지 못했으면 이 값 자체를 만들지 않는다.
	 * @property evidence 원문 근거 (표의 해당 행)
	 */
	data class ExtractedValue(
		val metricCode: String,
		val periodScope: PeriodScope,
		val rawAccountName: String?,
		val rawValue: String?,
		val rawUnit: SourceUnit?,
		val normalizedValue: BigDecimal,
		val evidence: String?,
	)
}
