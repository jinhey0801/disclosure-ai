package com.herenas.disclosureai.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AdminTokenFilterTest {

	private fun run(filter: AdminTokenFilter, method: String, uri: String, token: String?): Int {
		val request = MockHttpServletRequest(method, uri)
		token?.let { request.addHeader(AdminTokenFilter.HEADER, it) }
		val response = MockHttpServletResponse()
		filter.doFilter(request, response, MockFilterChain())
		return response.status
	}

	@Test
	fun `토큰이 맞으면 관리 API를 통과시킨다`() {
		assertThat(run(AdminTokenFilter("secret"), "POST", "/api/admin/collect", "secret")).isEqualTo(200)
	}

	@Test
	fun `토큰이 없거나 틀리면 막는다`() {
		val filter = AdminTokenFilter("secret")
		assertThat(run(filter, "POST", "/api/admin/collect", null)).isEqualTo(401)
		assertThat(run(filter, "POST", "/api/admin/collect", "wrong")).isEqualTo(401)
	}

	@Test
	fun `서버에 토큰이 설정되지 않으면 관리 API는 항상 막는다`() {
		assertThat(run(AdminTokenFilter(""), "POST", "/api/admin/collect", "")).isEqualTo(401)
	}

	@Test
	fun `조회 API와 화면은 토큰 없이 열린다`() {
		val filter = AdminTokenFilter("secret")
		assertThat(run(filter, "GET", "/api/companies", null)).isEqualTo(200)
		assertThat(run(filter, "GET", "/", null)).isEqualTo(200)
	}
}
