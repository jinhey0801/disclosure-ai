package com.herenas.disclosureai.collect;

import com.herenas.disclosureai.support.SingleRunJob;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 전체 기업 정답 수집 (1분 이상 걸려 백그라운드로 실행) */
@Component
@RequiredArgsConstructor
public class CollectJob {

	private final CollectService collectService;
	private final SingleRunJob<List<CollectService.Summary>> job = new SingleRunJob<>("collect-job");

	public SingleRunJob.Status<List<CollectService.Summary>> start(LocalDate from) {
		return job.start(() -> collectService.collectAll(from));
	}

	public SingleRunJob.Status<List<CollectService.Summary>> status() {
		return job.status();
	}

	@PreDestroy
	void shutdown() {
		job.shutdown();
	}
}
