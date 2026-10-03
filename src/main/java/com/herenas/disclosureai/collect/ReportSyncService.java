package com.herenas.disclosureai.collect;

import com.herenas.disclosureai.collect.ReportTitleParser.ParsedTitle;
import com.herenas.disclosureai.dart.DartClient;
import com.herenas.disclosureai.dart.DartResponses.Report;
import com.herenas.disclosureai.domain.company.Company;
import com.herenas.disclosureai.domain.report.DisclosureReport;
import com.herenas.disclosureai.domain.report.DisclosureReportRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DART 정기공시 목록을 disclosure_report 에 반영한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSyncService {

	private final DartClient dartClient;
	private final DisclosureReportRepository reportRepository;

	/**
	 * 첨부서류만 고친 정정공시. 본문(재무제표)은 원본 공시에 그대로 있고,
	 * DART 재무제표 API 도 원본 접수번호 기준 값을 준다.
	 */
	private static final Pattern ATTACHMENT_ONLY = Pattern.compile("^\\[(첨부정정|첨부추가)\\]");

	@Transactional
	public List<DisclosureReport> sync(Company company, LocalDate from, LocalDate to) {
		Map<ParsedTitle, Report> latestByPeriod = new HashMap<>();
		for (Report dart : dartClient.periodicReports(company.getCorpCode(), from, to)) {
			if (ATTACHMENT_ONLY.matcher(dart.reportNm()).find()) {
				continue;
			}
			Optional<ParsedTitle> parsed = ReportTitleParser.parse(dart.reportNm());
			if (parsed.isEmpty()) {
				log.warn("보고서 제목을 해석하지 못함: {} {}", company.getCorpName(), dart.reportNm());
				continue;
			}
			// 같은 기간이면 접수번호(접수일+일련번호)가 큰 쪽이 최신 정정본
			latestByPeriod.merge(parsed.get(), dart,
					(a, b) -> a.rceptNo().compareTo(b.rceptNo()) >= 0 ? a : b);
		}
		latestByPeriod.forEach((title, dart) -> upsert(company, dart, title));
		return reportRepository.findByCompanyOrderByPeriodEndAsc(company);
	}

	private void upsert(Company company, Report dart, ParsedTitle title) {
		LocalDate filedOn = LocalDate.parse(dart.rceptDt(), DateTimeFormatter.BASIC_ISO_DATE);
		reportRepository.findByCompanyAndFiscalYearAndReportType(company, title.fiscalYear(), title.reportType())
				.ifPresentOrElse(existing -> {
					if (!existing.getRceptNo().equals(dart.rceptNo())) {
						log.info("정정공시 반영: {} {} {} -> {}", company.getCorpName(), dart.reportNm(),
								existing.getRceptNo(), dart.rceptNo());
						existing.amend(dart.rceptNo(), dart.reportNm(), filedOn);
					}
				}, () -> reportRepository.save(new DisclosureReport(company, dart.rceptNo(), title.reportType(),
						title.fiscalYear(), title.periodEnd(), dart.reportNm(), filedOn)));
	}
}
