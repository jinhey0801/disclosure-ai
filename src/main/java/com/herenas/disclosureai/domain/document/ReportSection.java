package com.herenas.disclosureai.domain.document;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.metric.StatementType;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공시 원문에서 잘라낸 재무제표 한 개 (LLM 입력 단위) */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportSection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id")
	private DisclosureReport report;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 4)
	private FsDiv fsDiv;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private StatementType statementType;

	@Column(nullable = false)
	private Integer seq;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 20)
	private String unitLabel;

	@Column(nullable = false, columnDefinition = "MEDIUMTEXT")
	private String content;

	@Column(nullable = false)
	private Integer charCount;

	@Column(nullable = false, length = 30)
	private String sourceClass;

	@Column(nullable = false)
	private LocalDateTime fetchedAt;

	public ReportSection(DisclosureReport report, FsDiv fsDiv, StatementType statementType, int seq, String title,
			String unitLabel, String content, String sourceClass) {
		this.report = report;
		this.fsDiv = fsDiv;
		this.statementType = statementType;
		this.seq = seq;
		this.title = title;
		this.unitLabel = unitLabel;
		this.content = content;
		this.charCount = content.length();
		this.sourceClass = sourceClass;
		this.fetchedAt = LocalDateTime.now();
	}
}
