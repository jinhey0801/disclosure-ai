package com.herenas.disclosureai.domain.extraction;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.common.SourceUnit;
import com.herenas.disclosureai.domain.metric.MetricDefinition;
import com.herenas.disclosureai.domain.report.DisclosureReport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** LLM 추출 결과. 정답과의 비교 키: (report, metric, fsDiv, periodScope) */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtractionResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id")
	private ExtractionRun run;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id")
	private DisclosureReport report;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "metric_code")
	private MetricDefinition metric;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 4)
	private FsDiv fsDiv;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private PeriodScope periodScope;

	@Column(length = 200)
	private String rawAccountName;

	@Column(length = 100)
	private String rawValue;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private SourceUnit rawUnit;

	/** 원/명 단위. null 이면 원문에서 찾지 못한 것. */
	@Column(precision = 20, scale = 0)
	private BigDecimal normalizedValue;

	@Column(columnDefinition = "TEXT")
	private String evidenceText;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	private ExtractionResult(ExtractionRun run, DisclosureReport report, MetricDefinition metric, FsDiv fsDiv,
			PeriodScope periodScope, String rawAccountName, String rawValue, SourceUnit rawUnit,
			BigDecimal normalizedValue, String evidenceText) {
		this.run = run;
		this.report = report;
		this.metric = metric;
		this.fsDiv = fsDiv;
		this.periodScope = periodScope;
		this.rawAccountName = rawAccountName;
		this.rawValue = rawValue;
		this.rawUnit = rawUnit;
		this.normalizedValue = normalizedValue;
		this.evidenceText = evidenceText;
	}
}
