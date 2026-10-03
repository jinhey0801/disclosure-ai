package com.herenas.disclosureai.document;

import com.herenas.disclosureai.dart.DartClient;
import com.herenas.disclosureai.document.FinancialStatementParser.ParsedSection;
import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.document.ReportSection;
import com.herenas.disclosureai.domain.document.ReportSectionRepository;
import com.herenas.disclosureai.domain.metric.StatementType;
import com.herenas.disclosureai.domain.report.DisclosureReport;
import com.herenas.disclosureai.domain.report.DisclosureReportRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 공시 원문을 받아 재무제표 본문을 report_section 에 저장한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

	private final DartClient dartClient;
	private final FinancialStatementParser parser;
	private final DisclosureReportRepository reportRepository;
	private final ReportSectionRepository sectionRepository;
	private final TransactionTemplate transactionTemplate;

	public record Result(String report, int sections, int chars, List<String> warnings) {
	}

	public record Summary(int fetched, int skipped, int failed, long totalChars, List<String> warnings) {
	}

	/** 전체 보고서. force=false 면 이미 받은 보고서는 건너뛴다. */
	public Summary fetchAll(boolean force) {
		int fetched = 0;
		int skipped = 0;
		int failed = 0;
		long chars = 0;
		List<String> warnings = new ArrayList<>();
		for (DisclosureReport report : reportRepository.findAll()) {
			if (!force && sectionRepository.existsByReport(report)) {
				skipped++;
				continue;
			}
			try {
				Result result = fetch(report.getId());
				fetched++;
				chars += result.chars();
				warnings.addAll(result.warnings());
			} catch (RuntimeException e) {
				failed++;
				log.error("원문 수집 실패: report {}", report.getId(), e);
				warnings.add(report.getId() + " " + report.getTitle() + " 실패: " + e.getMessage());
			}
		}
		return new Summary(fetched, skipped, failed, chars, warnings);
	}

	public Result fetch(Long reportId) {
		String[] info = transactionTemplate.execute(tx -> {
			DisclosureReport r = reportRepository.findById(reportId).orElseThrow();
			return new String[] {r.getRceptNo(), r.getCompany().getCorpName() + " " + r.getTitle()};
		});
		String rceptNo = info[0];
		String label = info[1];
		// 다운로드는 트랜잭션 밖에서 (DB 커넥션을 붙잡지 않도록)
		String xml = mainDocument(dartClient.document(rceptNo), rceptNo);
		List<ParsedSection> parsed = parser.parse(xml);

		transactionTemplate.executeWithoutResult(tx -> {
			DisclosureReport managed = reportRepository.findById(reportId).orElseThrow();
			sectionRepository.deleteByReport(managed);
			sectionRepository.saveAll(parsed.stream()
					.map(s -> new ReportSection(managed, s.fsDiv(), s.statementType(), s.seq(), s.title(),
							s.unitLabel(), s.content(), s.sourceClass()))
					.toList());
		});

		List<String> warnings = new ArrayList<>();
		// 별도재무제표는 모든 회사에 있어야 한다. 연결은 종속회사가 있는 회사만.
		for (StatementType type : List.of(StatementType.BS, StatementType.IS)) {
			if (parsed.stream().noneMatch(s -> s.fsDiv() == FsDiv.OFS && s.statementType() == type)) {
				warnings.add(label + ": 별도 " + type + " 표를 찾지 못함");
			}
		}
		parsed.stream()
				.filter(s -> s.unitLabel() != null && !"원".equals(s.unitLabel()))
				.forEach(s -> warnings.add(label + ": " + s.title() + " 단위 " + s.unitLabel()));
		int chars = parsed.stream().mapToInt(s -> s.content().length()).sum();
		return new Result(label, parsed.size(), chars, warnings);
	}

	/** zip 안에서 본문(접수번호.xml)만 꺼낸다. 나머지는 첨부 감사보고서. */
	static String mainDocument(byte[] zip, String rceptNo) {
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				if (entry.getName().equals(rceptNo + ".xml")) {
					return new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		throw new IllegalStateException("본문 XML 이 없음: " + rceptNo);
	}
}
