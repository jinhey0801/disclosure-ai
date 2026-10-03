package com.herenas.disclosureai.domain.company

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

@Entity
class Company(
	/** DART 고유번호(8자리) */
	@Column(nullable = false, unique = true, length = 8)
	val corpCode: String,

	@Column(length = 6)
	val stockCode: String?,

	@Column(nullable = false, length = 100)
	val corpName: String,

	@Column(nullable = false, length = 20)
	val market: String,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null
		protected set

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	var createdAt: LocalDateTime? = null
		protected set
}

interface CompanyRepository : JpaRepository<Company, Long> {

	fun findByCorpCode(corpCode: String): Company?
}
