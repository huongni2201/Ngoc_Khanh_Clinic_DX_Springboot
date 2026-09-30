package com.ngockhanh.clinic.identity.infrastructure.security;

import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.query.AuthenticateSessionQuery;
import com.ngockhanh.clinic.identity.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public final class SessionFilter extends OncePerRequestFilter {
    private static final String SESSION_COOKIE = "NKC_SESSION";
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/logout");

    private final AuthenticateSessionUseCase authenticateSession;
    private final ApiResponseWriter errors;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod()) || PUBLIC_ENDPOINTS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<String> sessionIds = request.getCookies() == null ? List.of()
                : Arrays.stream(request.getCookies()).filter(cookie -> SESSION_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue).toList();
        if (!sessionIds.isEmpty()) {
            try {
                var principal = authenticateSession.execute(new AuthenticateSessionQuery(sessionIds));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
                SecurityContextHolder.setContext(context);
            } catch (AuthenticationFailure failure) {
                errors.write(response, failure);
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
