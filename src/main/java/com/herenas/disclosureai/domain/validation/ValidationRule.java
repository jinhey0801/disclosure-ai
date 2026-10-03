package com.herenas.disclosureai.domain.validation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 검증 규칙. 규칙의 "종류"는 코드(RuleType)로, 대상 항목과 임계값은 params(JSON)로 둔다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValidationRule {

	public enum RuleType { BALANCE_IDENTITY, CAPITAL_IMPAIRMENT, PERIOD_CHANGE }

	public enum Severity { ERROR, WARNING }

	@Id
	@Column(length = 40)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RuleType ruleType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, Object> params;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Severity severity;

	@Column(nullable = false)
	private boolean enabled;

	@Column(length = 500)
	private String description;
}
