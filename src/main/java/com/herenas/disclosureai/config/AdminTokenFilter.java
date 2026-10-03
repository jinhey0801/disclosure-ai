package com.herenas.disclosureai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/admin/** 는 X-Admin-Token 헤더가 ADMIN_TOKEN 과 같을 때만 허용한다.
 * 외부 공개 시 수집·추출(외부 API 호출과 비용 발생)을 아무나 실행하지 못하게 하는 최소 장치.
 * ADMIN_TOKEN 이 비어 있으면 관리 API 는 항상 막힌다.
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Admin-Token";

	private final byte[] token;

	public AdminTokenFilter(@Value("${admin.token:}") String token) {
		this.token = StringUtils.hasText(token) ? token.getBytes(StandardCharsets.UTF_8) : null;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/admin/") && !request.getRequestURI().equals("/api/admin");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String given = request.getHeader(HEADER);
		if (token == null || given == null
				|| !MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8))) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "관리자 토큰이 필요합니다.");
			return;
		}
		chain.doFilter(request, response);
	}
}
