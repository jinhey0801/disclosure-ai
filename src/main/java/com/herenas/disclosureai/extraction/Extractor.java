package com.herenas.disclosureai.extraction;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.common.SourceUnit;
import com.herenas.disclosureai.domain.metric.MetricSpec;
import com.herenas.disclosureai.domain.metric.StatementType;
import com.herenas.disclosureai.domain.report.ReportType;
import java.math.BigDecimal;
import java.util.List;

/**
 * 재무제표 본문 → 항목 값. 규칙 기반·LLM 등 구현을 바꿔 끼워도 배치·검증·평가는 그대로 쓴다.
 * 호출 단위는 (보고서 하나, 연결/별도 하나).
 */
public interface Extractor {

	/** extraction_run.model 에 기록된다 (예: rule-based, claude-…) */
	String name();

	/** extraction_run.prompt_version 에 기록된다 (규칙·프롬프트를 바꾸면 올린다) */
	String version();

	List<ExtractedValue> extract(Input input);

	record Input(ReportType reportType, int fiscalYear, FsDiv fsDiv, List<Section> sections,
			List<MetricSpec> metrics) {

		public List<Section> sectionsOf(StatementType type) {
			return sections.stream().filter(s -> s.statementType() == type).toList();
		}
	}

	record Section(StatementType statementType, int seq, String title, String unitLabel, String content) {
	}

	/**
	 * @param normalizedValue 원/명 단위. 찾지 못했으면 이 값 자체를 만들지 않는다.
	 * @param evidence 원문 근거 (표의 해당 행)
	 */
	record ExtractedValue(String metricCode, PeriodScope periodScope, String rawAccountName, String rawValue,
			SourceUnit rawUnit, BigDecimal normalizedValue, String evidence) {
	}
}
