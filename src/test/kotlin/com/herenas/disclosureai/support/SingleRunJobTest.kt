package com.herenas.disclosureai.support

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SingleRunJobTest {

	private val job = SingleRunJob<String>("test-job")

	@AfterEach
	fun tearDown() = job.shutdown()

	@Test
	fun `시작하면 바로 RUNNING을 돌려주고 끝나면 DONE과 결과를 남긴다`() {
		val release = CountDownLatch(1)

		val started = job.start {
			release.await(5, TimeUnit.SECONDS)
			"결과"
		}
		assertThat(started.state).isEqualTo(SingleRunJob.State.RUNNING)
		release.countDown()

		awaitState(SingleRunJob.State.DONE)
		assertThat(job.status().result).isEqualTo("결과")
		assertThat(job.status().finishedAt).isAfterOrEqualTo(job.status().startedAt)
	}

	@Test
	fun `실행 중에는 다시 시작할 수 없다`() {
		val release = CountDownLatch(1)
		job.start {
			release.await(5, TimeUnit.SECONDS)
			""
		}

		assertThatThrownBy { job.start { "" } }.isInstanceOf(IllegalStateException::class.java)
		release.countDown()
	}

	@Test
	fun `예외가 나면 FAILED와 메시지를 남긴다`() {
		job.start { throw IllegalStateException("DART_API_KEY 없음") }

		awaitState(SingleRunJob.State.FAILED)
		assertThat(job.status().error).isEqualTo("DART_API_KEY 없음")
	}

	private fun awaitState(expected: SingleRunJob.State) {
		val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
		while (job.status().state != expected && System.nanoTime() < deadline) Thread.sleep(20)
		assertThat(job.status().state).isEqualTo(expected)
	}
}
