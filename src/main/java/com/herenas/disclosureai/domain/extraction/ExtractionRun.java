package com.herenas.disclosureai.domain.extraction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 추출 1회 실행 단위. 모델/프롬프트 버전별 정확도 비교의 기준이 된다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtractionRun {

	public enum Status { RUNNING, COMPLETED, FAILED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String model;

	@Column(nullable = false, length = 50)
	private String promptVersion;

	/** 입력 변형 (InputVariant 이름). 원문이면 ORIGINAL */
	@Column(nullable = false, length = 20)
	private String inputVariant;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	/** Spring Batch 잡 실행 ID (진행 건수 조회용) */
	private Long jobExecutionId;

	@Column(nullable = false)
	private LocalDateTime startedAt;

	private LocalDateTime finishedAt;

	@Column(length = 500)
	private String note;

	public ExtractionRun(String model, String promptVersion, String inputVariant, String note) {
		this.model = model;
		this.promptVersion = promptVersion;
		this.inputVariant = inputVariant;
		this.note = note;
		this.status = Status.RUNNING;
		this.startedAt = LocalDateTime.now();
	}

	public void attachJob(Long jobExecutionId) {
		this.jobExecutionId = jobExecutionId;
	}

	public void finish(Status status, String note) {
		this.status = status;
		this.finishedAt = LocalDateTime.now();
		if (note != null) {
			this.note = note.length() > 500 ? note.substring(0, 497) + "..." : note;
		}
	}
}
