package com.herenas.disclosureai.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CollectJobTest {

	private final CollectService collectService = mock(CollectService.class);
	private final CollectJob job = new CollectJob(collectService);

	@Test
	void 시작하면_바로_RUNNING을_돌려주고_끝나면_DONE이_된다() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		List<CollectService.Summary> summaries = List.of(new CollectService.Summary("1", "A", 7, 142, List.of()));
		when(collectService.collectAll(any())).thenAnswer(inv -> {
			release.await(5, TimeUnit.SECONDS);
			return summaries;
		});

		assertThat(job.start(LocalDate.of(2025, 1, 1)).state()).isEqualTo(CollectJob.State.RUNNING);
		release.countDown();

		awaitState(CollectJob.State.DONE);
		assertThat(job.status().summaries()).isEqualTo(summaries);
	}

	@Test
	void 실행_중에는_다시_시작할_수_없다() {
		CountDownLatch release = new CountDownLatch(1);
		when(collectService.collectAll(any())).thenAnswer(inv -> {
			release.await(5, TimeUnit.SECONDS);
			return List.of();
		});

		job.start(LocalDate.of(2025, 1, 1));
		assertThatThrownBy(() -> job.start(LocalDate.of(2025, 1, 1))).isInstanceOf(IllegalStateException.class);
		release.countDown();
	}

	@Test
	void 예외가_나면_FAILED와_메시지를_남긴다() throws Exception {
		when(collectService.collectAll(any())).thenThrow(new IllegalStateException("DART_API_KEY 없음"));

		job.start(LocalDate.of(2025, 1, 1));

		awaitState(CollectJob.State.FAILED);
		assertThat(job.status().error()).isEqualTo("DART_API_KEY 없음");
	}

	private void awaitState(CollectJob.State expected) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (job.status().state() != expected && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
		assertThat(job.status().state()).isEqualTo(expected);
	}
}
