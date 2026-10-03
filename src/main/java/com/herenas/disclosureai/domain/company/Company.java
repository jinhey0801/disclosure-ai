package com.herenas.disclosureai.domain.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** DART 고유번호(8자리) */
	@Column(nullable = false, unique = true, length = 8)
	private String corpCode;

	@Column(length = 6)
	private String stockCode;

	@Column(nullable = false, length = 100)
	private String corpName;

	@Column(nullable = false, length = 20)
	private String market;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public Company(String corpCode, String stockCode, String corpName, String market) {
		this.corpCode = corpCode;
		this.stockCode = stockCode;
		this.corpName = corpName;
		this.market = market;
	}
}
