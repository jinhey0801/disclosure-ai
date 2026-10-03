package com.herenas.disclosureai.collect;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

	@PostMapping
	public List<CollectService.Summary> collectAll(
			@RequestParam(defaultValue = "2025-01-01") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
		return collectService.collectAll(from);
	}

	@PostMapping("/{corpCode}")
	public CollectService.Summary collect(@PathVariable String corpCode,
			@RequestParam(defaultValue = "2025-01-01") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
		return collectService.collect(corpCode, from);
	}
}
