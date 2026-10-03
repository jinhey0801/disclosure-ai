package com.herenas.disclosureai.collect;

import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 전체 수집을 백그라운드에서 한 번에 하나만 실행한다.
 * 18개사 수집은 1분 이상 걸려 리버스 프록시(60초)·Cloudflare(100초) 제한을 넘기므로
 * 요청은 시작만 하고 바로 응답하고, 화면은 상태를 주기적으로 조회한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectJob {

	public enum State { IDLE, RUNNING, DONE, FAILED }

	public record Status(State state, LocalDateTime startedAt, LocalDateTime finishedAt,
			List<CollectService.Summary> summaries, String error) {

		static final Status IDLE = new Status(State.IDLE, null, null, List.of(), null);
	}

	private final CollectService collectService;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "collect-job"));

	private volatile Status status = Status.IDLE;

	/** @throws IllegalStateException 이미 실행 중일 때 */
	public synchronized Status start(LocalDate from) {
		if (status.state() == State.RUNNING) {
			throw new IllegalStateException("이미 수집이 실행 중입니다.");
		}
		LocalDateTime startedAt = LocalDateTime.now();
		status = new Status(State.RUNNING, startedAt, null, List.of(), null);
		executor.submit(() -> {
			try {
				List<CollectService.Summary> summaries = collectService.collectAll(from);
				status = new Status(State.DONE, startedAt, LocalDateTime.now(), summaries, null);
			} catch (RuntimeException e) {
				log.error("전체 수집 실패", e);
				status = new Status(State.FAILED, startedAt, LocalDateTime.now(), List.of(), e.getMessage());
			}
		});
		return status;
	}

	public Status status() {
		return status;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}
}
