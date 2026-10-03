package com.herenas.disclosureai.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SingleRunJobTest {

	private final SingleRunJob<String> job = new SingleRunJob<>("test-job");

	@AfterEach
	void tearDown() {
		job.shutdown();
	}

	@Test
	void 시작하면_바로_RUNNING을_돌려주고_끝나면_DONE과_결과를_남긴다() throws Exception {
		CountDownLatch release = new CountDownLatch(1);

		SingleRunJob.Status<String> started = job.start(() -> {
			await(release);
			return "결과";
		});
		assertThat(started.state()).isEqualTo(SingleRunJob.State.RUNNING);
		release.countDown();

		awaitState(SingleRunJob.State.DONE);
		assertThat(job.status().result()).isEqualTo("결과");
	}

	@Test
	void 실행_중에는_다시_시작할_수_없다() {
		CountDownLatch release = new CountDownLatch(1);
		job.start(() -> {
			await(release);
			return "";
		});

		assertThatThrownBy(() -> job.start(() -> "")).isInstanceOf(IllegalStateException.class);
		release.countDown();
	}

	@Test
	void 예외가_나면_FAILED와_메시지를_남긴다() throws Exception {
		job.start(() -> {
			throw new IllegalStateException("DART_API_KEY 없음");
		});

		awaitState(SingleRunJob.State.FAILED);
		assertThat(job.status().error()).isEqualTo("DART_API_KEY 없음");
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void awaitState(SingleRunJob.State expected) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (job.status().state() != expected && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
		assertThat(job.status().state()).isEqualTo(expected);
	}
}
