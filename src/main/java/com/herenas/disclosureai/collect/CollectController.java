package com.herenas.disclosureai.collect;

import com.herenas.disclosureai.support.SingleRunJob;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리용: 보고서 목록과 정답을 DART 에서 수집한다. from 이후 제출된 정기공시가 대상. */
@RestController
@RequestMapping("/api/admin/collect")
@RequiredArgsConstructor
public class CollectController {

	private final CollectService collectService;
	private final CollectJob collectJob;

	/** 전체 기업 수집을 백그라운드로 시작한다. 진행 상태는 GET 으로 확인. */
	@PostMapping
	public ResponseEntity<SingleRunJob.Status<List<CollectService.Summary>>> collectAll(
			@RequestParam(defaultValue = "2025-01-01") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
		try {
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(collectJob.start(from));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(collectJob.status());
		}
	}

	@GetMapping
	public SingleRunJob.Status<List<CollectService.Summary>> status() {
		return collectJob.status();
	}

	/** 한 기업만 수집 (수 초 내 끝나므로 동기 실행) */
	@PostMapping("/{corpCode}")
	public CollectService.Summary collect(@PathVariable String corpCode,
			@RequestParam(defaultValue = "2025-01-01") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
		return collectService.collect(corpCode, from);
	}
}
