package com.herenas.disclosureai.extraction.batch;

import com.herenas.disclosureai.domain.extraction.ExtractionRun;
import com.herenas.disclosureai.domain.extraction.ExtractionRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 잡 시작·종료를 extraction_run 상태에 반영한다. */
@Component
@RequiredArgsConstructor
public class ExtractionRunListener implements JobExecutionListener {

	private final ExtractionRunRepository runRepository;
	private final TransactionTemplate transactionTemplate;

	@Override
	public void beforeJob(JobExecution jobExecution) {
		update(jobExecution, run -> run.attachJob(jobExecution.getId()));
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		boolean ok = jobExecution.getStatus() == BatchStatus.COMPLETED;
		String note = ok ? null : jobExecution.getAllFailureExceptions().stream()
				.map(Throwable::getMessage).findFirst().orElse(jobExecution.getExitStatus().getExitDescription());
		update(jobExecution, run -> run.finish(ok ? ExtractionRun.Status.COMPLETED : ExtractionRun.Status.FAILED, note));
	}

	private void update(JobExecution jobExecution, java.util.function.Consumer<ExtractionRun> change) {
		Long runId = jobExecution.getJobParameters().getLong("runId");
		transactionTemplate.executeWithoutResult(tx -> runRepository.findById(runId).ifPresent(change));
	}
}
