package com.ngockhanh.clinic.identity.infrastructure.configuration;

import com.ngockhanh.clinic.identity.application.AuthSettings;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.infrastructure.security.JwtSettings;
import com.ngockhanh.clinic.identity.infrastructure.security.ServerJwtTokens;
import com.ngockhanh.clinic.identity.infrastructure.session.LoginThrottleSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;

@Configuration
public class AuthInfrastructureConfiguration {
  @Bean
  Clock identityClock() {
    return Clock.systemUTC();
  }

  @Bean
  AuthSettings authSettings(Environment environment) {
    return new AuthSettings(
        environment.getProperty("clinic.auth.idle-timeout", Duration.class, Duration.ofMinutes(30)),
        environment.getProperty("clinic.auth.absolute-timeout", Duration.class, Duration.ofHours(8)));
  }

  @Bean
  JwtSettings jwtSettings(Environment environment) {
    return new JwtSettings(environment.getProperty("clinic.auth.issuer", "nkc-clinic"),
        environment.getProperty("clinic.auth.audience", "nkc-staff"),
        environment.getProperty("clinic.auth.jwt-key", ""));
  }

  @Bean
  LoginThrottleSettings loginThrottleSettings(Environment environment) {
    return new LoginThrottleSettings(
        environment.getProperty("clinic.auth.username-limit", Integer.class, 10),
        environment.getProperty("clinic.auth.ip-limit", Integer.class, 60),
        environment.getProperty("clinic.auth.throttle-window", Duration.class, Duration.ofMinutes(15)));
  }

  @Bean
  SessionTokens sessionTokens(JwtSettings jwtSettings, AuthSettings authSettings, Clock clock) {
    return new ServerJwtTokens(jwtSettings, authSettings, clock);
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
}
