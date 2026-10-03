package com.herenas.disclosureai.view;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.common.PeriodScope;
import com.herenas.disclosureai.domain.company.Company;
import com.herenas.disclosureai.domain.company.CompanyRepository;
import com.herenas.disclosureai.domain.metric.MetricDefinitionRepository;
import com.herenas.disclosureai.domain.report.DisclosureReportRepository;
import com.herenas.disclosureai.domain.report.ReportType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 조회 화면(static/index.html)용 읽기 전용 API */
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroundTruthViewController {

	private final EntityManager em;
	private final CompanyRepository companyRepository;
	private final DisclosureReportRepository reportRepository;
	private final MetricDefinitionRepository metricRepository;

	public record CompanyRow(String corpCode, String corpName, String stockCode, long reports, long groundTruths) {
	}

	public record ReportColumn(Long id, int fiscalYear, ReportType reportType, LocalDate periodEnd, String rceptNo,
			String title) {
	}

	public record MetricRow(String code, String standardName, String statementType, boolean evaluated,
			String dartAccountId) {
	}

	public record Value(Long reportId, String metricCode, FsDiv fsDiv, PeriodScope periodScope, BigDecimal value,
			String sourceAccountId, String sourceAccountName) {
	}

	public record CompanyDetail(CompanyRow company, List<ReportColumn> reports, List<MetricRow> metrics,
			List<Value> values) {
	}

	@GetMapping
	public List<CompanyRow> companies() {
		return em.createQuery("""
				select new com.herenas.disclosureai.view.GroundTruthViewController$CompanyRow(
				    c.corpCode, c.corpName, c.stockCode, count(distinct r.id), count(g.id))
				from Company c
				left join DisclosureReport r on r.company = c
				left join GroundTruth g on g.report = r
				group by c.id, c.corpCode, c.corpName, c.stockCode
				order by c.corpName
				""", CompanyRow.class).getResultList();
	}

	@GetMapping("/{corpCode}")
	public CompanyDetail company(@PathVariable String corpCode) {
		Company company = companyRepository.findByCorpCode(corpCode)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		CompanyRow row = companies().stream().filter(c -> c.corpCode().equals(corpCode)).findFirst().orElseThrow();

		List<ReportColumn> reports = reportRepository.findByCompanyOrderByPeriodEndAsc(company).stream()
				.map(r -> new ReportColumn(r.getId(), r.getFiscalYear(), r.getReportType(), r.getPeriodEnd(),
						r.getRceptNo(), r.getTitle()))
				.toList();
		List<MetricRow> metrics = metricRepository.findAll().stream()
				.filter(m -> m.isEnabled())
				.sorted(Comparator.comparing(m -> m.getStatementType().ordinal()))
				.map(m -> new MetricRow(m.getCode(), m.getStandardName(), m.getStatementType().name(), m.isEvaluated(),
						m.getDartAccountId()))
				.toList();
		List<Value> values = em.createQuery("""
				select new com.herenas.disclosureai.view.GroundTruthViewController$Value(
				    g.report.id, g.metric.code, g.fsDiv, g.periodScope, g.value, g.sourceAccountId,
				    g.sourceAccountName)
				from GroundTruth g
				where g.report.company = :company
				""", Value.class).setParameter("company", company).getResultList();
		return new CompanyDetail(row, reports, metrics, values);
	}
}
