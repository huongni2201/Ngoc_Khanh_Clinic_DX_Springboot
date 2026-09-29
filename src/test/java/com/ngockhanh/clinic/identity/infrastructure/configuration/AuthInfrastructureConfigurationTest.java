package com.ngockhanh.clinic.identity.infrastructure.configuration;

import com.ngockhanh.clinic.identity.domain.valueobject.StaffSessionPolicy;
import com.ngockhanh.clinic.identity.infrastructure.security.JwtSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuthInfrastructureConfigurationTest {
    @Test
    void applicationAndAdapterSettingsKeepExistingDefaults() {
        var environment = new MockEnvironment();
        var configuration = new AuthInfrastructureConfiguration();

        assertThat(configuration.staffSessionPolicy(environment))
                .isEqualTo(new StaffSessionPolicy(
                        Duration.ofMinutes(30), Duration.ofHours(8)));
        assertThat(configuration.jwtSettings(environment))
                .isEqualTo(new JwtSettings("nkc-clinic", "nkc-staff", ""));
        assertThat(configuration.loginThrottleSettings(environment).usernameLimit()).isEqualTo(10);
        assertThat(configuration.loginThrottleSettings(environment).ipLimit()).isEqualTo(60);
        assertThat(configuration.loginThrottleSettings(environment).window()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void startupRejectsMissingInvalidAndShortJwtKeys() {
        var configuration = new AuthInfrastructureConfiguration();
        var environment = new MockEnvironment();
        var sessionPolicy = configuration.staffSessionPolicy(environment);
        for (String key : new String[]{"", "not-base64!", "c2hvcnQ="}) {
            var jwtSettings = new JwtSettings("nkc", "nkc-staff", key);
            assertThatThrownBy(() -> configuration.sessionTokens(jwtSettings, sessionPolicy, Clock.systemUTC()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void configurationTypesRejectInvalidSessionAndThrottleLimits() {
        assertThatThrownBy(() -> new StaffSessionPolicy(
                Duration.ofHours(9), Duration.ofHours(8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new com.ngockhanh.clinic.identity.infrastructure.session.LoginThrottleSettings(
                0, 60, Duration.ofMinutes(15))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accountTransactionTemplatesKeepReadAndWriteBoundaries() {
        var configuration = new AuthInfrastructureConfiguration();
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

        TransactionTemplate read = (TransactionTemplate) configuration.staffAccountReadTransaction(manager);
        TransactionTemplate snapshot = (TransactionTemplate) configuration.staffAccountSnapshotTransaction(manager);
        TransactionTemplate write = (TransactionTemplate) configuration.staffAccountWriteTransaction(manager);

        assertThat(read.isReadOnly()).isTrue();
        assertThat(snapshot.isReadOnly()).isTrue();
        assertThat(snapshot.getIsolationLevel())
                .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThat(write.isReadOnly()).isFalse();
    }
}
