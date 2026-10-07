package com.ngockhanh.clinic.accesscontrol.infrastructure.configuration;

import com.ngockhanh.clinic.accesscontrol.api.http.SessionCookieFactory;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SessionPolicy;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.JsonSecurityErrorHandler;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.OriginCheckFilter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.SessionCookieAuthenticationFilter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.UserPasswordEncoder;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP security: stateless session-cookie authentication, CORS and Origin checks for state-changing
 * requests, and the default access rules (ADR-0014). Per-endpoint permission rules are added here
 * with {@code hasAuthority("PERM_<code>")}.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AccessControlProperties.class)
public class SecurityConfiguration {
  private static final String PARTICIPANTS_PATH =
      "/api/v1/organizations/*/health-examination-batches/*/participants";
  private static final String EXAMINATION_DETAILS_PATH =
      "/api/v1/organizations/*/health-examination-batches/*/examination-details";
  private static final String PAYMENT_SUMMARY_PATH =
      "/api/v1/organizations/*/health-examination-batches/*/reports/payment-summary";

  /** A staff account that also holds the given permission; checked before the controller runs. */
  private static AuthorizationManager<RequestAuthorizationContext> staffWith(String permission) {
    return AuthorizationManagers.allOf(
        AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority("ACCOUNT_STAFF"),
        AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority(permission));
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AccessControlProperties properties,
      AuthenticateSessionUseCase authenticateSession,
      JsonSecurityErrorHandler errors) {
    var corsConfiguration = new CorsConfiguration();
    corsConfiguration.setAllowedOrigins(properties.allowedOrigins());
    corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
    corsConfiguration.setAllowedHeaders(List.of("Accept", "Content-Type", "Idempotency-Key"));
    corsConfiguration.setExposedHeaders(List.of("Content-Disposition", "Retry-After"));
    corsConfiguration.setAllowCredentials(true);
    var corsConfigurationSource = new UrlBasedCorsConfigurationSource();
    corsConfigurationSource.registerCorsConfiguration("/api/**", corsConfiguration);

    var originCheck = new OriginCheckFilter(properties.allowedOrigins(), errors);
    var sessionAuthentication =
        new SessionCookieAuthenticationFilter(authenticateSession, properties.cookieName(), errors);
    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
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
                    .requestMatchers(HttpMethod.GET, PARTICIPANTS_PATH)
                    .access(staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_READ"))
                    .requestMatchers(HttpMethod.GET, PARTICIPANTS_PATH + "/import-template")
                    .access(staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_READ"))
                    .requestMatchers(HttpMethod.POST, PARTICIPANTS_PATH + "/imports")
                    .access(staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_IMPORT"))
                    .requestMatchers(HttpMethod.POST, PARTICIPANTS_PATH)
                    .access(staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_MANAGE"))
                    .requestMatchers(HttpMethod.GET, PARTICIPANTS_PATH + "/*")
                    .access(staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_MANAGE"))
                    .requestMatchers(HttpMethod.PUT, PARTICIPANTS_PATH + "/*")
                    .access(staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_MANAGE"))
                    .requestMatchers(HttpMethod.DELETE, PARTICIPANTS_PATH + "/*")
                    .access(staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_MANAGE"))
                    .requestMatchers(HttpMethod.POST, PARTICIPANTS_PATH + "/*/reactivate")
                    .access(staffWith("PERM_HEALTH_EXAMINATION_PARTICIPANT_MANAGE"))
                    .requestMatchers(
                        HttpMethod.GET,
                        EXAMINATION_DETAILS_PATH,
                        EXAMINATION_DETAILS_PATH + "/summary",
                        EXAMINATION_DETAILS_PATH + "/export")
                    .access(staffWith("PERM_HEALTH_EXAMINATION_SERVICE_READ"))
                    .requestMatchers(HttpMethod.POST, EXAMINATION_DETAILS_PATH + "/imports")
                    .access(staffWith("PERM_HEALTH_EXAMINATION_SERVICE_RECONCILE"))
                    .requestMatchers(
                        HttpMethod.GET, PAYMENT_SUMMARY_PATH, PAYMENT_SUMMARY_PATH + "/docx")
                    .access(staffWith("PERM_HEALTH_EXAMINATION_REPORT_READ"))
                    .requestMatchers("/api/v1/**")
                    .hasAuthority("ACCOUNT_STAFF")
                    .anyRequest()
                    .denyAll());
    return http.build();
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
}
