package com.herenas.disclosureai.support;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * 오래 걸리는 관리 작업을 백그라운드에서 한 번에 하나만 실행한다.
 * 요청은 시작만 하고 바로 응답하고(리버스 프록시 60초 제한 회피), 화면은 status() 를 주기적으로 조회한다.
 * 상태는 메모리에만 있어 앱이 재시작되면 IDLE 로 돌아간다.
 */
@Slf4j
public class SingleRunJob<T> {

	public enum State { IDLE, RUNNING, DONE, FAILED }

	public record Status<T>(State state, LocalDateTime startedAt, LocalDateTime finishedAt, T result, String error) {
	}

	private final String name;
	private final ExecutorService executor;
	private volatile Status<T> status = new Status<>(State.IDLE, null, null, null, null);

	public SingleRunJob(String name) {
		this.name = name;
		this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, name));
	}

	/** @throws IllegalStateException 이미 실행 중일 때 */
	public synchronized Status<T> start(Supplier<T> task) {
		if (status.state() == State.RUNNING) {
			throw new IllegalStateException(name + " 작업이 이미 실행 중입니다.");
		}
		LocalDateTime startedAt = LocalDateTime.now();
		status = new Status<>(State.RUNNING, startedAt, null, null, null);
		executor.submit(() -> {
			try {
				T result = task.get(); // 종료 시각은 작업이 끝난 뒤에 잰다
				status = new Status<>(State.DONE, startedAt, LocalDateTime.now(), result, null);
			} catch (RuntimeException e) {
				log.error("{} 작업 실패", name, e);
				status = new Status<>(State.FAILED, startedAt, LocalDateTime.now(), null, e.getMessage());
			}
		});
		return status;
	}

	public Status<T> status() {
		return status;
	}

	public void shutdown() {
		executor.shutdownNow();
	}
}
