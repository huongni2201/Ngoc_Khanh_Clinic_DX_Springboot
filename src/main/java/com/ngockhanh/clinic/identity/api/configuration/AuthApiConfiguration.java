package com.ngockhanh.clinic.identity.api.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Set;

@Configuration
public class AuthApiConfiguration {
  @Bean
  public AuthApiSettings authApiSettings(Environment environment) {
    Set<String> profiles = Set.of(environment.getActiveProfiles());
    boolean development = profiles.contains("local") || profiles.contains("test");
    rejectMixedProfiles(profiles, development);
    return new AuthApiSettings(!development,
        csv(environment.getProperty("clinic.auth.trusted-proxies", "")));
  }

  private static void rejectMixedProfiles(Set<String> profiles, boolean development) {
    if (development && (profiles.contains("prod") || profiles.contains("production"))) {
      throw new IllegalStateException("Production cannot run with local/test profiles");
    }
  }

  private static java.util.List<String> csv(String value) {
    return Arrays.stream(value.split(",")).map(String::strip).filter(part -> !part.isEmpty()).toList();
  }
}
