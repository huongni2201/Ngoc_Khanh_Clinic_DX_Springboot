package com.ngockhanh.clinic.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

class AuthConfigurationTest {
    @Test void defaultConfigurationDeniesBusinessAccessAndRequiresSecureCookie() {
        var settings = new AuthConfiguration().authSettings(new MockEnvironment());
        assertThat(settings.businessAccess()).isFalse();
        assertThat(settings.secureCookie()).isTrue();
    }

    @Test void productionCannotBeCombinedWithEitherDevelopmentProfile() {
        for (String production : new String[]{"prod", "production"}) {
            for (String development : new String[]{"local", "test"}) {
                var env = new MockEnvironment();
                env.setActiveProfiles(production, development);
                assertThatThrownBy(() -> new AuthConfiguration().authSettings(env)).isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Test void startupRejectsAbsentInvalidAndShortJwtKeys() {
        var config = new AuthConfiguration();
        var env = new MockEnvironment();
        var settings = config.authSettings(env);
        for (String key : new String[]{"", "not-base64!", "c2hvcnQ="}) {
            env.setProperty("clinic.auth.jwt-key", key);
            assertThatThrownBy(() -> config.sessionTokens(env, settings, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
