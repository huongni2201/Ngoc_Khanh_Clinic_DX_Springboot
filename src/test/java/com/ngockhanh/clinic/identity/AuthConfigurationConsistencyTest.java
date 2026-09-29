package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.api.configuration.AuthApiConfiguration;
import com.ngockhanh.clinic.identity.infrastructure.configuration.AuthSecurityConfiguration;
import com.ngockhanh.clinic.identity.infrastructure.configuration.AuthSecuritySettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthConfigurationConsistencyTest {
  @Test
  void apiAndInfrastructureCookiePoliciesMatchForEachSupportedProfile() {
    var apiConfiguration = new AuthApiConfiguration();
    var securityConfiguration = new AuthSecurityConfiguration();

    for (String[] profiles : new String[][]{{}, {"local"}, {"test"}, {"prod"}, {"production"}}) {
      var environment = new MockEnvironment();
      environment.setActiveProfiles(profiles);

      var apiSettings = apiConfiguration.authApiSettings(environment);
      var securitySettings = securityConfiguration.authSecuritySettings(environment);

      assertThat(apiSettings.secureCookie()).isEqualTo(securitySettings.secureCookie());
      assertThat(securitySettings.businessAccess()).isEqualTo(
          profiles.length > 0 && (profiles[0].equals("local") || profiles[0].equals("test")));
    }
  }

  @Test
  void preservesOriginConfigurationAndRejectsWildcards() {
    var environment = new MockEnvironment()
        .withProperty("NKC_AUTH_ALLOWED_ORIGINS", "https://staff.example.vn, https://admin.example.vn");
    assertThat(new AuthSecurityConfiguration().authSecuritySettings(environment).allowedOrigins())
        .containsExactly("https://staff.example.vn", "https://admin.example.vn");
    assertThatThrownBy(() -> new AuthSecuritySettings(true, false, java.util.List.of("*")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void infrastructureRejectsProductionCombinedWithDevelopmentProfiles() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("production", "test");

    assertThatThrownBy(() -> new AuthSecurityConfiguration().authSecuritySettings(environment))
        .isInstanceOf(IllegalStateException.class);
  }
}
