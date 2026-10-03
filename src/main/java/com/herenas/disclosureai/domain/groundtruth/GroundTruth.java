package com.herenas.disclosureai.domain.groundtruth;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.common.PeriodScope;
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

/** DART Open API 에서 수집한 정답. ExtractionResult 와 같은 키로 비교한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroundTruth {

	public enum Source { DART_FS_API, DART_EMP_API }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

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

	/** 원/명 단위 */
	@Column(nullable = false, precision = 20, scale = 0)
	private BigDecimal value;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Source source;

	@Column(length = 100)
	private String sourceAccountId;

	@Column(length = 200)
	private String sourceAccountName;

	@Column(nullable = false)
	private LocalDateTime fetchedAt;

	@Builder
	private GroundTruth(DisclosureReport report, MetricDefinition metric, FsDiv fsDiv, PeriodScope periodScope,
			BigDecimal value, Source source, String sourceAccountId, String sourceAccountName) {
		this.report = report;
		this.metric = metric;
		this.fsDiv = fsDiv;
		this.periodScope = periodScope;
		this.value = value;
		this.source = source;
		this.sourceAccountId = sourceAccountId;
		this.sourceAccountName = sourceAccountName;
		this.fetchedAt = LocalDateTime.now();
	}
}
