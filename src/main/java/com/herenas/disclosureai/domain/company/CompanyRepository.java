package com.herenas.disclosureai.domain.company;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

	Optional<Company> findByCorpCode(String corpCode);
}
