package com.herenas.disclosureai.evaluation;

import com.herenas.disclosureai.domain.extraction.ExtractionRun;
import com.herenas.disclosureai.domain.extraction.ExtractionRunRepository;
import com.herenas.disclosureai.extraction.ExtractionLauncher;
import com.herenas.disclosureai.extraction.InputVariant;
import com.herenas.disclosureai.validation.ValidationService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RunController {

	private final ExtractionLauncher launcher;
	private final ExtractionRunRepository runRepository;
	private final JobExplorer jobExplorer;
	private final EvaluationService evaluationService;
	private final ValidationService validationService;

	public record StepView(String name, String status, long read, long written, long skipped) {
	}

	public record VariantView(String name, String label) {
	}

	public record RunView(Long id, String extractor, String version, String inputVariant, String inputVariantLabel,
			ExtractionRun.Status status,
			LocalDateTime startedAt, LocalDateTime finishedAt, String note, List<StepView> steps) {
	}

	@GetMapping("/api/extractors")
	public List<String> extractors() {
		return launcher.extractorNames();
	}

	@GetMapping("/api/variants")
	public List<VariantView> variants() {
		return Arrays.stream(InputVariant.values()).map(v -> new VariantView(v.name(), v.label())).toList();
	}

	/** 관리용: 추출 실행 시작 (Spring Batch, 백그라운드) */
	@PostMapping("/api/admin/extractions")
	public ResponseEntity<?> start(@RequestParam(defaultValue = "rule-based") String extractor,
			@RequestParam(defaultValue = "ORIGINAL") InputVariant variant) throws Exception {
		try {
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(view(launcher.start(extractor, variant)));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
		}
	}

	@GetMapping("/api/runs")
	public List<RunView> runs() {
		return runRepository.findAllByOrderByIdDesc().stream().map(this::view).toList();
	}

	@GetMapping("/api/runs/{runId}/evaluation")
	public EvaluationService.Evaluation evaluation(@PathVariable Long runId) {
		return evaluationService.evaluate(runId);
	}

	@GetMapping("/api/runs/{runId}/validation")
	public ValidationService.Summary validation(@PathVariable Long runId) {
		return validationService.storedResults(runId);
	}

	/** 정답 데이터에 검증 규칙을 돌린 결과 (저장하지 않음) */
	@GetMapping("/api/ground-truth/validation")
	public ValidationService.Summary groundTruthValidation() {
		return validationService.validateGroundTruth();
	}

	private RunView view(ExtractionRun run) {
		List<StepView> steps = List.of();
		if (run.getJobExecutionId() != null) {
			JobExecution execution = jobExplorer.getJobExecution(run.getJobExecutionId());
			if (execution != null) {
				steps = execution.getStepExecutions().stream()
						.sorted(java.util.Comparator.comparing(StepExecution::getId))
						.map(s -> new StepView(s.getStepName(), s.getStatus().name(), s.getReadCount(),
								s.getWriteCount(), s.getSkipCount()))
						.toList();
			}
		}
		InputVariant variant = InputVariant.valueOf(run.getInputVariant());
		return new RunView(run.getId(), run.getModel(), run.getPromptVersion(), variant.name(), variant.label(),
				run.getStatus(), run.getStartedAt(),
				run.getFinishedAt(), run.getNote(), steps);
	}
}
