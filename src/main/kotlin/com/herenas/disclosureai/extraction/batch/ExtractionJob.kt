package com.herenas.disclosureai.extraction.batch

import com.herenas.disclosureai.domain.common.FsDiv
import com.herenas.disclosureai.domain.document.ReportSectionRepository
import com.herenas.disclosureai.domain.extraction.ExtractionResult
import com.herenas.disclosureai.domain.extraction.ExtractionResultRepository
import com.herenas.disclosureai.domain.extraction.ExtractionRun
import com.herenas.disclosureai.domain.extraction.ExtractionRunRepository
import com.herenas.disclosureai.domain.metric.MetricDefinitionRepository
import com.herenas.disclosureai.domain.metric.MetricSpec
import com.herenas.disclosureai.domain.report.DisclosureReport
import com.herenas.disclosureai.extraction.Extractor
import com.herenas.disclosureai.extraction.InputVariant
import com.herenas.disclosureai.validation.ValidationService
import jakarta.persistence.EntityManager
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.support.ListItemReader
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 추출 잡: extractStep(보고서별 추출, 10건씩 커밋) → validateStep(검증 규칙 실행).
 *
 * 보고서 하나가 실패해도 건너뛰고 계속한다(skip). LLM 추출기를 붙이면 여기서 재시도·호출 속도 제한을 더한다.
 */
@Configuration
class ExtractionJobConfig {

	@Bean
	fun extractionJob(jobRepository: JobRepository, extractStep: Step, validateStep: Step, listener: ExtractionRunListener): Job =
		JobBuilder(JOB_NAME, jobRepository)
			.listener(listener)
			.start(extractStep)
			.next(validateStep)
			.build()

	@Bean
	fun extractStep(
		jobRepository: JobRepository,
		transactionManager: PlatformTransactionManager,
		reportIdReader: ListItemReader<Long>,
		processor: ExtractionItemProcessor,
		extractionWriter: ItemWriter<List<ExtractionResult>>,
	): Step = StepBuilder("extractStep", jobRepository)
		.chunk<Long, List<ExtractionResult>>(CHUNK_SIZE, transactionManager)
		.reader(reportIdReader)
		.processor(processor)
		.writer(extractionWriter)
		.faultTolerant()
		.skip(RuntimeException::class.java)
		.skipLimit(SKIP_LIMIT)
		.build()

	/** 원문 본문이 있는 보고서만 읽는다 */
	@Bean
	@StepScope
	fun reportIdReader(sectionRepository: ReportSectionRepository): ListItemReader<Long> =
		ListItemReader(sectionRepository.findReportIdsWithSections())

	@Bean
	fun extractionWriter(repository: ExtractionResultRepository): ItemWriter<List<ExtractionResult>> =
		ItemWriter { chunk -> chunk.items.forEach { repository.saveAll(it) } }

	@Bean
	fun validateStep(
		jobRepository: JobRepository,
		transactionManager: PlatformTransactionManager,
		validationService: ValidationService,
	): Step = StepBuilder("validateStep", jobRepository)
		.tasklet({ _, context ->
			val runId = context.stepContext.jobParameters["runId"] as Long
			validationService.validateRun(runId)
			RepeatStatus.FINISHED
		}, transactionManager)
		.build()

	companion object {
		const val JOB_NAME = "extractionJob"
		private const val CHUNK_SIZE = 10
		private const val SKIP_LIMIT = 20
	}
}

/** 보고서 하나 → 연결·별도 각각 추출기를 돌려 extraction_result 엔티티 목록으로 만든다. */
@Component
@StepScope
class ExtractionItemProcessor(
	private val em: EntityManager,
	private val sectionRepository: ReportSectionRepository,
	private val metricRepository: MetricDefinitionRepository,
	extractors: List<Extractor>,
	@Value("#{jobParameters['runId']}") private val runId: Long,
	@Value("#{jobParameters['extractor']}") extractorName: String,
	@Value("#{jobParameters['variant'] ?: 'ORIGINAL'}") variant: String,
) : ItemProcessor<Long, List<ExtractionResult>> {

	private val extractor = extractors.firstOrNull { it.name == extractorName }
		?: throw IllegalArgumentException("알 수 없는 추출기: $extractorName")
	private val variant = InputVariant.valueOf(variant)

	override fun process(reportId: Long): List<ExtractionResult> {
		val report = em.find(DisclosureReport::class.java, reportId)
		val metrics = metricRepository.findByEnabledTrue()
		val metricByCode = metrics.associateBy { it.code }
		val specs = metrics.map(MetricSpec::from)
		val sections = sectionRepository.findByReportIdOrderByFsDivAscStatementTypeAscSeqAsc(reportId)
		val run = em.getReference(ExtractionRun::class.java, runId)

		return listOf(FsDiv.CFS, FsDiv.OFS).flatMap { fsDiv ->
			val input = sections.filter { it.fsDiv == fsDiv }
				.map { Extractor.Section(it.statementType, it.seq, it.title, it.unitLabel, it.content) }
			if (input.isEmpty()) return@flatMap emptyList()
			extractor.extract(Extractor.Input(report.reportType, report.fiscalYear, fsDiv, variant.apply(input), specs))
				.map {
					ExtractionResult(
						run = run,
						report = report,
						metric = metricByCode.getValue(it.metricCode),
						fsDiv = fsDiv,
						periodScope = it.periodScope,
						rawAccountName = it.rawAccountName?.take(200),
						rawValue = it.rawValue?.take(100),
						rawUnit = it.rawUnit,
						normalizedValue = it.normalizedValue,
						evidenceText = it.evidence,
					)
				}
		}
	}
}

/** 잡 시작·종료를 extraction_run 상태에 반영한다. */
@Component
class ExtractionRunListener(
	private val runRepository: ExtractionRunRepository,
	private val transactionTemplate: TransactionTemplate,
) : JobExecutionListener {

	override fun beforeJob(jobExecution: JobExecution) {
		update(jobExecution) { it.attachJob(jobExecution.id) }
	}

	override fun afterJob(jobExecution: JobExecution) {
		val ok = jobExecution.status == BatchStatus.COMPLETED
		val note = if (ok) {
			null
		} else {
			jobExecution.allFailureExceptions.firstOrNull()?.message ?: jobExecution.exitStatus.exitDescription
		}
		update(jobExecution) { it.finish(if (ok) ExtractionRun.Status.COMPLETED else ExtractionRun.Status.FAILED, note) }
	}

	private fun update(jobExecution: JobExecution, change: (ExtractionRun) -> Unit) {
		val runId = jobExecution.jobParameters.getLong("runId") ?: return
		transactionTemplate.executeWithoutResult { runRepository.findByIdOrNull(runId)?.let(change) }
	}
}
