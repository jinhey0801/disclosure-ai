package com.herenas.disclosureai.domain.report;

import com.herenas.disclosureai.domain.company.Company;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id")
	private Company company;

	/** DART 접수번호. 정정공시는 접수번호가 따로 나온다. */
	@Column(nullable = false, unique = true, length = 14)
	private String rceptNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ReportType reportType;

	@Column(nullable = false)
	private Integer fiscalYear;

	@Column(nullable = false)
	private LocalDate periodEnd;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false)
	private LocalDate filedOn;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public DisclosureReport(Company company, String rceptNo, ReportType reportType, Integer fiscalYear,
			LocalDate periodEnd, String title, LocalDate filedOn) {
		this.company = company;
		this.rceptNo = rceptNo;
		this.reportType = reportType;
		this.fiscalYear = fiscalYear;
		this.periodEnd = periodEnd;
		this.title = title;
		this.filedOn = filedOn;
	}

	/** 정정공시가 나오면 같은 기간 보고서를 최신 접수번호로 갱신한다. */
	public void amend(String rceptNo, String title, LocalDate filedOn) {
		this.rceptNo = rceptNo;
		this.title = title;
		this.filedOn = filedOn;
	}
}
