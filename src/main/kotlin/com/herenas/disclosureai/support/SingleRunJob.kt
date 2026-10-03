package com.herenas.disclosureai.support

import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 오래 걸리는 관리 작업을 백그라운드에서 한 번에 하나만 실행한다.
 * 요청은 시작만 하고 바로 응답하고(리버스 프록시 60초 제한 회피), 화면은 status() 를 주기적으로 조회한다.
 * 상태는 메모리에만 있어 앱이 재시작되면 IDLE 로 돌아간다.
 */
class SingleRunJob<T>(private val name: String) {

	enum class State { IDLE, RUNNING, DONE, FAILED }

	data class Status<T>(
		val state: State,
		val startedAt: LocalDateTime? = null,
		val finishedAt: LocalDateTime? = null,
		val result: T? = null,
		val error: String? = null,
	)

	private val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, name) }

	@Volatile
	private var status: Status<T> = Status(State.IDLE)

	/** @throws IllegalStateException 이미 실행 중일 때 */
	@Synchronized
	fun start(task: () -> T): Status<T> {
		check(status.state != State.RUNNING) { "$name 작업이 이미 실행 중입니다." }
		val startedAt = LocalDateTime.now()
		status = Status(State.RUNNING, startedAt)
		executor.submit {
			status = try {
				val result = task() // 종료 시각은 작업이 끝난 뒤에 잰다
				Status(State.DONE, startedAt, LocalDateTime.now(), result)
			} catch (e: RuntimeException) {
				log.error("{} 작업 실패", name, e)
				Status(State.FAILED, startedAt, LocalDateTime.now(), error = e.message)
			}
		}
		return status
	}

	fun status(): Status<T> = status

	fun shutdown() {
		executor.shutdownNow()
	}

	companion object {
		private val log = LoggerFactory.getLogger(SingleRunJob::class.java)
	}
}
