package com.ngockhanh.clinic.identity.infrastructure.configuration;

import com.ngockhanh.clinic.identity.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import com.ngockhanh.clinic.identity.infrastructure.security.SessionFilter;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
public class AuthSecurityConfiguration {
    @Bean
    CookieCsrfTokenRepository csrfRepository(AuthHttpSettings settings) {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(settings.secureCookie())
                .sameSite("Lax").path("/"));
        return repository;
    }

    @Bean
    SecurityFilterChain userSecurity(HttpSecurity http, AuthenticateSessionUseCase authenticateSession,
                                      AuthHttpSettings settings, CookieCsrfTokenRepository csrf,
                                      ApiResponseWriter errors) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(security -> security.securityContextRepository(
                        new org.springframework.security.web.context.NullSecurityContextRepository()))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .csrf(csrfConfig -> csrfConfig.csrfTokenRepository(csrf))
                .cors(cors -> cors.disable())
                .exceptionHandling(errorsConfig -> errorsConfig
                        .authenticationEntryPoint((request, response, exception) ->
                                errors.write(response, 401, "Invalid credentials or session"))
                        .accessDeniedHandler((request, response, exception) -> errors.write(response, 403, "Access denied")))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                            .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout-all").authenticated();
                    if (settings.businessAccess()) {
                        authorize.requestMatchers("/api/v1/**").access((authentication, context) -> {
                            var current = authentication.get();
                            boolean allowed = current.isAuthenticated()
                                    && current.getPrincipal() instanceof UserPrincipal principal
                                    && "STAFF".equals(principal.principalType())
                                    && !principal.roleAssignments().isEmpty();
                            return new AuthorizationDecision(allowed);
                        });
                    }
                    authorize.anyRequest().denyAll();
                })
                .addFilterBefore(corsFilter(settings, errors), CsrfFilter.class)
                .addFilterBefore(new SessionFilter(authenticateSession, errors), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource cors(AuthHttpSettings settings) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(settings.allowedOrigins());
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-CSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Retry-After"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private org.springframework.web.filter.CorsFilter corsFilter(AuthHttpSettings settings, ApiResponseWriter errors) {
        var filter = new org.springframework.web.filter.CorsFilter(cors(settings));
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response) throws IOException {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                response.getHeaders().setCacheControl("no-store");
                errors.write(response.getBody(), 403, "Origin not allowed");
                response.flush();
            }
        });
        return filter;
    }

}
