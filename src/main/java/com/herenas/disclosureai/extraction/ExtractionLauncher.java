package com.herenas.disclosureai.extraction;

import com.herenas.disclosureai.domain.extraction.ExtractionRun;
import com.herenas.disclosureai.domain.extraction.ExtractionRunRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 추출 실행을 만들고 배치 잡을 백그라운드로 띄운다. */
@Slf4j
@Service
public class ExtractionLauncher {

	private final ExtractionRunRepository runRepository;
	private final List<Extractor> extractors;
	private final Job extractionJob;
	private final TaskExecutorJobLauncher launcher;

	public ExtractionLauncher(ExtractionRunRepository runRepository, List<Extractor> extractors,
			Job extractionJob, JobRepository jobRepository) throws Exception {
		this.runRepository = runRepository;
		this.extractors = extractors;
		this.extractionJob = extractionJob;
		// 요청 스레드를 붙잡지 않도록 비동기 런처를 따로 둔다 (Boot 기본 런처는 동기)
		this.launcher = new TaskExecutorJobLauncher();
		this.launcher.setJobRepository(jobRepository);
		this.launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("extraction-"));
		this.launcher.afterPropertiesSet();
	}

	public List<String> extractorNames() {
		return extractors.stream().map(Extractor::name).toList();
	}

	/** @throws IllegalStateException 다른 실행이 진행 중일 때 */
	public synchronized ExtractionRun start(String extractorName, InputVariant variant) throws Exception {
		Extractor extractor = extractors.stream().filter(e -> e.name().equals(extractorName)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("알 수 없는 추출기: " + extractorName));
		if (runRepository.existsByStatus(ExtractionRun.Status.RUNNING)) {
			throw new IllegalStateException("이미 실행 중인 추출이 있습니다.");
		}
		ExtractionRun run = runRepository.save(
				new ExtractionRun(extractor.name(), extractor.version(), variant.name(), null));
		launcher.run(extractionJob, new JobParametersBuilder()
				.addLong("runId", run.getId())
				.addString("extractor", extractor.name())
				.addString("variant", variant.name())
				.toJobParameters());
		return run;
	}

	/** 앱이 재시작되면 실행 중이던 잡은 끊긴다. 남은 RUNNING 표시를 정리한다. */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void markInterruptedRuns() {
		runRepository.findByStatus(ExtractionRun.Status.RUNNING).forEach(run -> {
			log.warn("앱 재시작으로 중단된 추출 실행: {}", run.getId());
			run.finish(ExtractionRun.Status.FAILED, "앱 재시작으로 중단됨");
		});
	}
}
