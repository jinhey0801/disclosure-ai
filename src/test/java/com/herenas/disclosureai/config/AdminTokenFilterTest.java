package com.herenas.disclosureai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminTokenFilterTest {

	private int run(AdminTokenFilter filter, String method, String uri, String token) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		if (token != null) {
			request.addHeader(AdminTokenFilter.HEADER, token);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response.getStatus();
	}

	@Test
	void 토큰이_맞으면_관리_API를_통과시킨다() throws Exception {
		assertThat(run(new AdminTokenFilter("secret"), "POST", "/api/admin/collect", "secret")).isEqualTo(200);
	}

	@Test
	void 토큰이_없거나_틀리면_막는다() throws Exception {
		AdminTokenFilter filter = new AdminTokenFilter("secret");
		assertThat(run(filter, "POST", "/api/admin/collect", null)).isEqualTo(401);
		assertThat(run(filter, "POST", "/api/admin/collect", "wrong")).isEqualTo(401);
	}

	@Test
	void 서버에_토큰이_설정되지_않으면_관리_API는_항상_막는다() throws Exception {
		assertThat(run(new AdminTokenFilter(""), "POST", "/api/admin/collect", "")).isEqualTo(401);
	}

	@Test
	void 조회_API와_화면은_토큰_없이_열린다() throws Exception {
		AdminTokenFilter filter = new AdminTokenFilter("secret");
		assertThat(run(filter, "GET", "/api/companies", null)).isEqualTo(200);
		assertThat(run(filter, "GET", "/", null)).isEqualTo(200);
	}
}
