package com.herenas.disclosureai.document

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.document.ReportSectionRepository
import com.herenas.disclosureai.domain.metric.StatementType
import com.herenas.disclosureai.support.SingleRunJob
import jakarta.annotation.PreDestroy
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class DocumentController(
	private val documentService: DocumentService,
	private val coverageService: CoverageService,
	private val sectionRepository: ReportSectionRepository,
) {
	private val job = SingleRunJob<DocumentService.Summary>("document-job")

	data class SectionView(
		val fsDiv: FsDiv,
		val statementType: StatementType,
		val seq: Int,
		val title: String,
		val unitLabel: String?,
		val charCount: Int,
		val content: String,
	)

	/** 관리용: 전체 보고서 원문 수집 시작 (백그라운드). force=true 면 이미 받은 것도 다시 받는다. */
	@PostMapping("/api/admin/documents")
	fun fetchAll(@RequestParam(defaultValue = "false") force: Boolean): ResponseEntity<SingleRunJob.Status<DocumentService.Summary>> =
		try {
			ResponseEntity.status(HttpStatus.ACCEPTED).body(job.start { documentService.fetchAll(force) })
		} catch (e: IllegalStateException) {
			ResponseEntity.status(HttpStatus.CONFLICT).body(job.status())
		}

	@GetMapping("/api/admin/documents")
	fun status(): SingleRunJob.Status<DocumentService.Summary> = job.status()

	/** 관리용: 보고서 하나만 다시 받기 */
	@PostMapping("/api/admin/documents/{reportId}")
	fun fetch(@PathVariable reportId: Long): DocumentService.Result = documentService.fetch(reportId)

	/** LLM 이 읽게 될 본문 */
	@GetMapping("/api/reports/{reportId}/sections")
	@Transactional(readOnly = true)
	fun sections(@PathVariable reportId: Long): List<SectionView> =
		sectionRepository.findByReportIdOrderByFsDivAscStatementTypeAscSeqAsc(reportId).map {
			SectionView(it.fsDiv, it.statementType, it.seq, it.title, it.unitLabel, it.charCount, it.content)
		}

	/** 정답 숫자가 본문에 들어 있는 비율 */
	@GetMapping("/api/documents/coverage")
	fun coverage(): CoverageService.Coverage = coverageService.check()

	@PreDestroy
	fun shutdown() = job.shutdown()
}
