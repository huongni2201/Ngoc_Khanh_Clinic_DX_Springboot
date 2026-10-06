package com.ngockhanh.clinic.accesscontrol.infrastructure.configuration;

import com.ngockhanh.clinic.accesscontrol.api.http.SessionCookieFactory;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SessionPolicy;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.JsonSecurityErrorHandler;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.OriginCheckFilter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.SessionCookieAuthenticationFilter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.UserPasswordEncoder;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP security: stateless session-cookie authentication, CORS and Origin checks for the allowed
 * frontend origins, and the default access rules (ADR-0014). Per-endpoint permission rules are
 * added here with {@code hasAuthority("PERM_<code>")}.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AccessControlProperties.class)
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AccessControlProperties properties,
      AuthenticateSessionUseCase authenticateSession,
      JsonSecurityErrorHandler errors) {
    var originCheck = new OriginCheckFilter(properties.allowedOrigins(), errors);
    var sessionAuthentication =
        new SessionCookieAuthenticationFilter(authenticateSession, properties.cookieName(), errors);
    http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors))
        .addFilterBefore(originCheck, AnonymousAuthenticationFilter.class)
        .addFilterBefore(sessionAuthentication, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            requests ->
                requests
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/me")
                    .authenticated()
                    .requestMatchers("/api/v1/**")
                    .hasAuthority("ACCOUNT_STAFF")
                    .anyRequest()
                    .denyAll());
    return http.build();
  }

  /**
   * Lets the browser frontend on an allowed origin call the API with the session cookie. Uses the
   * same allow-list as the Origin check; other origins get no CORS headers.
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource(AccessControlProperties properties) {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.allowedOrigins());
    cors.setAllowCredentials(true);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type", "Accept"));
    cors.setMaxAge(Duration.ofHours(1));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }

  @Bean
  JsonSecurityErrorHandler jsonSecurityErrorHandler(JsonMapper json) {
    return new JsonSecurityErrorHandler(json);
  }

  @Bean
  SessionPolicy sessionPolicy(AccessControlProperties properties) {
    return new SessionPolicy(properties.idleTimeout(), properties.absoluteTimeout());
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new UserPasswordEncoder();
  }

  @Bean
  SessionCookieFactory sessionCookieFactory(AccessControlProperties properties) {
    return new SessionCookieFactory(properties.cookieName(), properties.cookieSecure());
  }

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }
}
