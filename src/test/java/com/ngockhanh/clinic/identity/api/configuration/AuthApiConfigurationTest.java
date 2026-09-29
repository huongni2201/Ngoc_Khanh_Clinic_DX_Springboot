package com.ngockhanh.clinic.identity.api.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthApiConfigurationTest {
  @Test
  void productionDefaultsToSecureCookiesAndNoTrustedProxies() {
    var settings = new AuthApiConfiguration().authApiSettings(new MockEnvironment());
    assertThat(settings.secureCookie()).isTrue();
    assertThat(settings.trustedProxies()).isEmpty();
  }

  @Test
  void developmentProfilesDisableSecureCookiesAndRetainConfiguredTrustedProxies() {
    var environment = new MockEnvironment()
        .withProperty("clinic.auth.trusted-proxies", "127.0.0.1, ::1");
    environment.setActiveProfiles("local");

    var settings = new AuthApiConfiguration().authApiSettings(environment);

    assertThat(settings.secureCookie()).isFalse();
    assertThat(settings.trustedProxies()).containsExactly("127.0.0.1", "::1");
  }

  @Test
  void productionCannotBeCombinedWithEitherDevelopmentProfile() {
    for (String production : new String[]{"prod", "production"}) {
      for (String development : new String[]{"local", "test"}) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(production, development);
        assertThatThrownBy(() -> new AuthApiConfiguration().authApiSettings(environment))
            .isInstanceOf(IllegalStateException.class);
      }
    }
  }
}
