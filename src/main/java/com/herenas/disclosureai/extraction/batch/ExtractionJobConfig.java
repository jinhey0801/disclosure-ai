package com.herenas.disclosureai.extraction.batch;

import com.herenas.disclosureai.domain.document.ReportSectionRepository;
import com.herenas.disclosureai.domain.extraction.ExtractionResult;
import com.herenas.disclosureai.domain.extraction.ExtractionResultRepository;
import com.herenas.disclosureai.validation.ValidationService;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 추출 잡: extractStep(보고서별 추출, 10건씩 커밋) → validateStep(검증 규칙 실행).
 *
 * 보고서 하나가 실패해도 건너뛰고 계속한다(skip). LLM 추출기를 붙이면 여기서 재시도·호출 속도 제한을 더한다.
 */
@Configuration
public class ExtractionJobConfig {

	public static final String JOB_NAME = "extractionJob";
	private static final int CHUNK_SIZE = 10;
	private static final int SKIP_LIMIT = 20;

	@Bean
	public Job extractionJob(JobRepository jobRepository, Step extractStep, Step validateStep,
			ExtractionRunListener listener) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.listener(listener)
				.start(extractStep)
				.next(validateStep)
				.build();
	}

	@Bean
	public Step extractStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			ListItemReader<Long> reportIdReader, ExtractionItemProcessor processor,
			ItemWriter<List<ExtractionResult>> extractionWriter) {
		return new StepBuilder("extractStep", jobRepository)
				.<Long, List<ExtractionResult>>chunk(CHUNK_SIZE, transactionManager)
				.reader(reportIdReader)
				.processor(processor)
				.writer(extractionWriter)
				.faultTolerant()
				.skip(RuntimeException.class)
				.skipLimit(SKIP_LIMIT)
				.build();
	}

	/** 원문 본문이 있는 보고서만 읽는다 */
	@Bean
	@StepScope
	public ListItemReader<Long> reportIdReader(ReportSectionRepository sectionRepository) {
		return new ListItemReader<>(sectionRepository.findReportIdsWithSections());
	}

	@Bean
	public ItemWriter<List<ExtractionResult>> extractionWriter(ExtractionResultRepository repository) {
		return chunk -> chunk.getItems().forEach(repository::saveAll);
	}

	@Bean
	public Step validateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			ValidationService validationService) {
		return new StepBuilder("validateStep", jobRepository)
				.tasklet((contribution, context) -> {
					Long runId = (Long) context.getStepContext().getJobParameters().get("runId");
					validationService.validateRun(runId);
					return RepeatStatus.FINISHED;
				}, transactionManager)
				.build();
	}
}
