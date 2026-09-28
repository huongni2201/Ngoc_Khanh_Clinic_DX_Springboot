package com.ngockhanh.clinic.identity.infrastructure.security;

import com.ngockhanh.clinic.identity.application.usecase.*;
import com.ngockhanh.clinic.identity.application.port.*;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.*;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Supplier;

@Configuration
public class AuthConfiguration {
  @Bean
  Clock authClock() {
    return Clock.systemUTC();
  }

  @Bean
  AuthSettings authSettings(Environment env) {
    Set<String> profiles = Set.of(env.getActiveProfiles());
    boolean development = profiles.contains("local") || profiles.contains("test");
    if (development && (profiles.contains("prod") || profiles.contains("production"))) {
      throw new IllegalStateException("Production cannot run with local/test profiles");
    }
    return new AuthSettings(
        env.getProperty("clinic.auth.idle-timeout", Duration.class, Duration.ofMinutes(30)),
        env.getProperty("clinic.auth.absolute-timeout", Duration.class, Duration.ofHours(8)),
        env.getProperty("clinic.auth.issuer", "nkc-clinic"),
        env.getProperty("clinic.auth.audience", "nkc-staff"), !development, development,
        csv(env.getProperty("clinic.auth.allowed-origins",
            env.getProperty("NKC_AUTH_ALLOWED_ORIGINS", development ? "http://localhost:3000" : ""))),
        env.getProperty("clinic.auth.username-limit", Integer.class, 10),
        env.getProperty("clinic.auth.ip-limit", Integer.class, 60),
        env.getProperty("clinic.auth.throttle-window", Duration.class, Duration.ofMinutes(15)),
        csv(env.getProperty("clinic.auth.trusted-proxies", "")));
  }

  private static List<String> csv(String value) {
    return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
  }

  @Bean
  SessionTokens sessionTokens(Environment env, AuthSettings settings, Clock clock) {
    return new ServerJwtTokens(env.getProperty("clinic.auth.jwt-key", ""), settings, clock);
  }

  @Bean
  Supplier<String> sessionIds() {
    SecureRandom random = new SecureRandom();
    return () -> {
      byte[] bytes = new byte[32];
      random.nextBytes(bytes);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    };
  }

  @Bean
  CookieCsrfTokenRepository csrfRepository(AuthSettings settings) {
    var repository = new CookieCsrfTokenRepository();
    repository.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(settings.secureCookie()).sameSite("Lax").path("/"));
    return repository;
  }

  @Bean
  SecurityFilterChain staffSecurity(HttpSecurity http, StaffAuthentication authentication,
                                    AuthSettings settings, CookieCsrfTokenRepository csrf, JsonMapper json) throws Exception {
    var errors = new SecurityErrors(json);
    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(s -> s.securityContextRepository(new org.springframework.security.web.context.NullSecurityContextRepository()))
        .requestCache(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
        .csrf(c -> c.csrfTokenRepository(csrf))
        .cors(c -> c.disable())
        .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> errors.write(res, AuthenticationFailure.invalid()))
            .accessDeniedHandler((req, res, ex) -> errors.write(res, new AuthenticationFailure(403, "Access denied"))))
        .authorizeHttpRequests(a -> {
          a.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
              .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/staff/login", "/api/v1/auth/logout").permitAll()
              .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout-all").authenticated();
          if (settings.businessAccess()) a.requestMatchers("/api/v1/**").authenticated();
          a.anyRequest().denyAll();
        })
        .addFilterBefore(corsFilter(settings, json), CsrfFilter.class)
        .addFilterBefore(new StaffSessionFilter(authentication, errors), AnonymousAuthenticationFilter.class);
    return http.build();
  }

  private CorsConfigurationSource cors(AuthSettings settings) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(settings.origins());
    configuration.setAllowCredentials(true);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-CSRF-TOKEN"));
    configuration.setExposedHeaders(List.of("Retry-After"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private org.springframework.web.filter.CorsFilter corsFilter(AuthSettings settings, JsonMapper json) {
    var filter = new org.springframework.web.filter.CorsFilter(cors(settings));
    filter.setCorsProcessor(new DefaultCorsProcessor() {
      @Override
      protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response)
          throws java.io.IOException {
        response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        json.writeValue(response.getBody(), new com.ngockhanh.clinic.shared.web.ApiError(403, "Origin not allowed"));
        response.flush();
      }
    });
    return filter;
  }
}
