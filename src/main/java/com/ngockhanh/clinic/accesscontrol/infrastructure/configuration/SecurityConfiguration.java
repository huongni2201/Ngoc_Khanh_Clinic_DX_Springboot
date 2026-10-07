package com.ngockhanh.clinic.accesscontrol.infrastructure.configuration;

import com.ngockhanh.clinic.accesscontrol.api.http.SessionCookieFactory;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SessionPolicy;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.EndpointPermissions;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.JsonSecurityErrorHandler;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.OriginCheckFilter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.SessionCookieAuthenticationFilter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.UserPasswordEncoder;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP security: stateless session-cookie authentication, Origin checks for state-changing requests
 * (ADR-0014) and per-endpoint permissions (ADR-0015). Business endpoints without a rule in {@link
 * EndpointPermissions} are denied.
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
    http.csrf(csrf -> csrf.disable())
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
            requests -> {
              requests
                  .dispatcherTypeMatchers(DispatcherType.ERROR)
                  .permitAll()
                  .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/logout")
                  .permitAll()
                  .requestMatchers(HttpMethod.GET, "/api/v1/auth/me")
                  .authenticated();
              EndpointPermissions.apply(requests);
              requests.anyRequest().denyAll();
            });
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

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }
}
