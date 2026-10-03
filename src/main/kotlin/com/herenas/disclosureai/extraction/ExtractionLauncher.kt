package com.herenas.disclosureai.extraction

import com.herenas.disclosureai.domain.extraction.ExtractionRun
import com.herenas.disclosureai.domain.extraction.ExtractionRunRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 추출 실행을 만들고 배치 잡을 백그라운드로 띄운다. */
@Service
class ExtractionLauncher(
	private val runRepository: ExtractionRunRepository,
	private val extractors: List<Extractor>,
	private val extractionJob: Job,
	jobRepository: JobRepository,
) {
	// 요청 스레드를 붙잡지 않도록 비동기 런처를 따로 둔다 (Boot 기본 런처는 동기)
	private val launcher = TaskExecutorJobLauncher().apply {
		setJobRepository(jobRepository)
		setTaskExecutor(SimpleAsyncTaskExecutor("extraction-"))
		afterPropertiesSet()
	}

	fun extractorNames(): List<String> = extractors.map { it.name }

	/** @throws IllegalStateException 다른 실행이 진행 중일 때 */
	@Synchronized
	fun start(extractorName: String, variant: InputVariant): ExtractionRun {
		val extractor = extractors.firstOrNull { it.name == extractorName }
			?: throw IllegalArgumentException("알 수 없는 추출기: $extractorName")
		check(!runRepository.existsByStatus(ExtractionRun.Status.RUNNING)) { "이미 실행 중인 추출이 있습니다." }
		val run = runRepository.save(ExtractionRun(extractor.name, extractor.version, variant.name, null))
		launcher.run(
			extractionJob,
			JobParametersBuilder()
				.addLong("runId", run.id!!)
				.addString("extractor", extractor.name)
				.addString("variant", variant.name)
				.toJobParameters(),
		)
		return run
	}

	/** 앱이 재시작되면 실행 중이던 잡은 끊긴다. 남은 RUNNING 표시를 정리한다. */
	@EventListener(ApplicationReadyEvent::class)
	@Transactional
	fun markInterruptedRuns() {
		runRepository.findByStatus(ExtractionRun.Status.RUNNING).forEach {
			log.warn("앱 재시작으로 중단된 추출 실행: {}", it.id)
			it.finish(ExtractionRun.Status.FAILED, "앱 재시작으로 중단됨")
		}
	}

	companion object {
		private val log = LoggerFactory.getLogger(ExtractionLauncher::class.java)
	}
}
