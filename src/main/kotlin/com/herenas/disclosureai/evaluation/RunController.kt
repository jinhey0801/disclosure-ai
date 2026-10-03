package com.herenas.disclosureai.evaluation

import com.herenas.disclosureai.domain.extraction.ExtractionRun
import com.herenas.disclosureai.domain.extraction.ExtractionRunRepository
import com.herenas.disclosureai.extraction.ExtractionLauncher
import com.herenas.disclosureai.extraction.InputVariant
import com.herenas.disclosureai.validation.ValidationService
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
class RunController(
	private val launcher: ExtractionLauncher,
	private val runRepository: ExtractionRunRepository,
	private val jobExplorer: JobExplorer,
	private val evaluationService: EvaluationService,
	private val validationService: ValidationService,
) {
	data class StepView(val name: String, val status: String, val read: Long, val written: Long, val skipped: Long)

	data class VariantView(val name: String, val label: String)

	data class RunView(
		val id: Long,
		val extractor: String,
		val version: String,
		val inputVariant: String,
		val inputVariantLabel: String,
		val status: ExtractionRun.Status,
		val startedAt: LocalDateTime,
		val finishedAt: LocalDateTime?,
		val note: String?,
		val steps: List<StepView>,
	)

	@GetMapping("/api/extractors")
	fun extractors(): List<String> = launcher.extractorNames()

	@GetMapping("/api/variants")
	fun variants(): List<VariantView> = InputVariant.entries.map { VariantView(it.name, it.label) }

	/** 관리용: 추출 실행 시작 (Spring Batch, 백그라운드) */
	@PostMapping("/api/admin/extractions")
	fun start(
		@RequestParam(defaultValue = "rule-based") extractor: String,
		@RequestParam(defaultValue = "ORIGINAL") variant: InputVariant,
	): ResponseEntity<*> =
		try {
			ResponseEntity.status(HttpStatus.ACCEPTED).body(view(launcher.start(extractor, variant)))
		} catch (e: IllegalArgumentException) {
			ResponseEntity.badRequest().body(mapOf("error" to e.message))
		} catch (e: IllegalStateException) {
			ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to e.message))
		}

	@GetMapping("/api/runs")
	fun runs(): List<RunView> = runRepository.findAllByOrderByIdDesc().map(::view)

	@GetMapping("/api/runs/{runId}/evaluation")
	fun evaluation(@PathVariable runId: Long): EvaluationService.Evaluation = evaluationService.evaluate(runId)

	@GetMapping("/api/runs/{runId}/validation")
	fun validation(@PathVariable runId: Long): ValidationService.Summary = validationService.storedResults(runId)

	/** 정답 데이터에 검증 규칙을 돌린 결과 (저장하지 않음) */
	@GetMapping("/api/ground-truth/validation")
	fun groundTruthValidation(): ValidationService.Summary = validationService.validateGroundTruth()

	private fun view(run: ExtractionRun): RunView {
		val steps = run.jobExecutionId?.let(jobExplorer::getJobExecution)?.stepExecutions.orEmpty()
			.sortedBy { it.id }
			.map { StepView(it.stepName, it.status.name, it.readCount, it.writeCount, it.skipCount) }
		val variant = InputVariant.valueOf(run.inputVariant)
		return RunView(
			run.id!!, run.model, run.promptVersion, variant.name, variant.label, run.status, run.startedAt,
			run.finishedAt, run.note, steps,
		)
	}
}
