package com.herenas.disclosureai.document;

import com.herenas.disclosureai.domain.common.FsDiv;
import com.herenas.disclosureai.domain.document.ReportSectionRepository;
import com.herenas.disclosureai.domain.metric.StatementType;
import com.herenas.disclosureai.support.SingleRunJob;
import jakarta.annotation.PreDestroy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DocumentController {

	private final DocumentService documentService;
	private final CoverageService coverageService;
	private final ReportSectionRepository sectionRepository;
	private final SingleRunJob<DocumentService.Summary> job = new SingleRunJob<>("document-job");

	public record SectionView(FsDiv fsDiv, StatementType statementType, int seq, String title, String unitLabel,
			int charCount, String content) {
	}

	/** 관리용: 전체 보고서 원문 수집 시작 (백그라운드). force=true 면 이미 받은 것도 다시 받는다. */
	@PostMapping("/api/admin/documents")
	public ResponseEntity<SingleRunJob.Status<DocumentService.Summary>> fetchAll(
			@RequestParam(defaultValue = "false") boolean force) {
		try {
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(job.start(() -> documentService.fetchAll(force)));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(job.status());
		}
	}

	@GetMapping("/api/admin/documents")
	public SingleRunJob.Status<DocumentService.Summary> status() {
		return job.status();
	}

	/** 관리용: 보고서 하나만 다시 받기 */
	@PostMapping("/api/admin/documents/{reportId}")
	public DocumentService.Result fetch(@PathVariable Long reportId) {
		return documentService.fetch(reportId);
	}

	/** LLM 이 읽게 될 본문 */
	@GetMapping("/api/reports/{reportId}/sections")
	@Transactional(readOnly = true)
	public List<SectionView> sections(@PathVariable Long reportId) {
		return sectionRepository.findByReportIdOrderByFsDivAscStatementTypeAscSeqAsc(reportId).stream()
				.map(s -> new SectionView(s.getFsDiv(), s.getStatementType(), s.getSeq(), s.getTitle(),
						s.getUnitLabel(), s.getCharCount(), s.getContent()))
				.toList();
	}

	/** 정답 숫자가 본문에 들어 있는 비율 */
	@GetMapping("/api/documents/coverage")
	public CoverageService.Coverage coverage() {
		return coverageService.check();
	}

	@PreDestroy
	void shutdown() {
		job.shutdown();
	}
}
