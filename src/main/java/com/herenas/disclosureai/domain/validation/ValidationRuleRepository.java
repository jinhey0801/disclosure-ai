package com.herenas.disclosureai.domain.validation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationRuleRepository extends JpaRepository<ValidationRule, String> {

	List<ValidationRule> findByEnabledTrue();
}
