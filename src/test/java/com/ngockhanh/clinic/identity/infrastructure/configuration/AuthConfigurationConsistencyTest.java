package com.ngockhanh.clinic.identity.infrastructure.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthConfigurationConsistencyTest {

    private final AuthInfrastructureConfiguration configuration = new AuthInfrastructureConfiguration();

    @Test
    void selectsHttpSettingsFromEachSupportedProfile() {
        assertSettings(new String[0], true, false, "");
        assertSettings(new String[]{"local"}, false, true, "http://localhost:3000");
        assertSettings(new String[]{"test"}, false, true, "http://localhost:3000");
        assertSettings(new String[]{"prod"}, true, false, "");
        assertSettings(new String[]{"production"}, true, false, "");
    }

    @Test
    void mixedProfilesKeepDevelopmentSettingsWithoutRejectingConfiguration() {
        for (String production : new String[]{"prod", "production"}) {
            for (String development : new String[]{"local", "test"}) {
                var environment = new MockEnvironment();
                environment.setActiveProfiles(production, development);

                AuthHttpSettings settings = configuration.authHttpSettings(environment);

                assertThat(settings.secureCookie()).isFalse();
                assertThat(settings.businessAccess()).isTrue();
                assertThat(settings.allowedOrigins()).containsExactly("http://localhost:3000");
            }
        }
    }

    @Test
    void honorsOriginsAndTrustedProxyOverridesAndRejectsWildcardOrigins() {
        var environment = new MockEnvironment()
                .withProperty("clinic.auth.allowed-origins", "https://staff.example.vn")
                .withProperty("NKC_AUTH_ALLOWED_ORIGINS", "https://ignored.example.vn")
                .withProperty("clinic.auth.trusted-proxies", "127.0.0.1, ::1");

        AuthHttpSettings settings = configuration.authHttpSettings(environment);

        assertThat(settings.allowedOrigins()).containsExactly("https://staff.example.vn");
        assertThat(settings.trustedProxies()).containsExactly("127.0.0.1", "::1");
        assertThatThrownBy(() -> new AuthHttpSettings(true, false, java.util.List.of("*"), java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertSettings(String[] profiles, boolean secureCookie, boolean businessAccess, String defaultOrigin) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);

        AuthHttpSettings settings = configuration.authHttpSettings(environment);

        assertThat(settings.secureCookie()).isEqualTo(secureCookie);
        assertThat(settings.businessAccess()).isEqualTo(businessAccess);
        if (defaultOrigin.isEmpty()) {
            assertThat(settings.allowedOrigins()).isEmpty();
        } else {
            assertThat(settings.allowedOrigins()).containsExactly(defaultOrigin);
        }
    }
}
