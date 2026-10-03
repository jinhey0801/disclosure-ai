package com.herenas.disclosureai.domain.validation;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.extraction.ExtractionRun;
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
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValidationResult {

	public enum Outcome { PASS, FAIL, SKIPPED }

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
	@JoinColumn(name = "rule_code")
	private ValidationRule rule;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 4)
	private FsDiv fsDiv;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Outcome outcome;

	@Column(length = 500)
	private String message;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> details;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public ValidationResult(ExtractionRun run, DisclosureReport report, ValidationRule rule, FsDiv fsDiv,
			Outcome outcome, String message, Map<String, Object> details) {
		this.run = run;
		this.report = report;
		this.rule = rule;
		this.fsDiv = fsDiv;
		this.outcome = outcome;
		this.message = message;
		this.details = details;
	}
}
