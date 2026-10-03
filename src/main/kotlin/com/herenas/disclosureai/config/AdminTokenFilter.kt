package com.herenas.disclosureai.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * /api/admin 아래 경로는 X-Admin-Token 헤더가 ADMIN_TOKEN 과 같을 때만 허용한다.
 * 외부 공개 시 수집·추출(외부 API 호출과 비용 발생)을 아무나 실행하지 못하게 하는 최소 장치.
 * ADMIN_TOKEN 이 비어 있으면 관리 API 는 항상 막힌다.
 */
@Component
class AdminTokenFilter(@Value("\${admin.token:}") token: String) : OncePerRequestFilter() {

	private val token: ByteArray? = token.takeIf { it.isNotBlank() }?.toByteArray()

	override fun shouldNotFilter(request: HttpServletRequest): Boolean =
		!request.requestURI.startsWith("/api/admin/") && request.requestURI != "/api/admin"

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
		val given = request.getHeader(HEADER)
		if (token == null || given == null || !MessageDigest.isEqual(token, given.toByteArray())) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "관리자 토큰이 필요합니다.")
			return
		}
		chain.doFilter(request, response)
	}

	companion object {
		const val HEADER = "X-Admin-Token"
	}
}
