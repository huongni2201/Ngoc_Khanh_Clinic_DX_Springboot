package com.ngockhanh.clinic.identity.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ngockhanh.clinic.audit.application.port.AuthAudit;
import com.ngockhanh.clinic.identity.application.port.LoginThrottle;
import com.ngockhanh.clinic.identity.application.port.Passwords;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.application.usecase.LoginUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutAllSessionsUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutSessionUseCase;
import com.ngockhanh.clinic.identity.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.identity.domain.valueobject.SessionPolicy;
import com.ngockhanh.clinic.identity.infrastructure.security.JwtSettings;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

class AuthInfrastructureConfigurationTest {
  @Test
  void authUseCasesSelectTheirNamedTransactionBoundaries() {
    TransactionOperations read = mock(TransactionOperations.class);
    TransactionOperations snapshot = mock(TransactionOperations.class);
    TransactionOperations write = mock(TransactionOperations.class);

    new ApplicationContextRunner()
        .withUserConfiguration(
            LoginUseCase.class, LogoutSessionUseCase.class, LogoutAllSessionsUseCase.class)
        .withBean(UserAccountRepository.class, () -> mock(UserAccountRepository.class))
        .withBean(AuthAudit.class, () -> mock(AuthAudit.class))
        .withBean(Passwords.class, () -> mock(Passwords.class))
        .withBean(SessionTokens.class, () -> mock(SessionTokens.class))
        .withBean(SessionStore.class, () -> mock(SessionStore.class))
        .withBean(LoginThrottle.class, () -> mock(LoginThrottle.class))
        .withBean(
            SessionPolicy.class,
            () -> new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(8)))
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(Supplier.class, () -> (Supplier<String>) () -> "session-id")
        .withBean("accountReadTransaction", TransactionOperations.class, () -> read)
        .withBean("accountSnapshotTransaction", TransactionOperations.class, () -> snapshot)
        .withBean("accountWriteTransaction", TransactionOperations.class, () -> write)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LoginUseCase.class);
              assertThat(context).hasSingleBean(LogoutSessionUseCase.class);
              assertThat(context).hasSingleBean(LogoutAllSessionsUseCase.class);
            });
  }

  @Test
  void applicationAndAdapterSettingsKeepExistingDefaults() {
    var environment = new MockEnvironment();
    var configuration = new AuthInfrastructureConfiguration();

    assertThat(configuration.sessionPolicy(environment))
        .isEqualTo(new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(8)));
    assertThat(configuration.jwtSettings(environment))
        .isEqualTo(new JwtSettings("nkc-clinic", "nkc-staff", ""));
    assertThat(configuration.loginThrottleSettings(environment).usernameLimit()).isEqualTo(10);
    assertThat(configuration.loginThrottleSettings(environment).ipLimit()).isEqualTo(60);
    assertThat(configuration.loginThrottleSettings(environment).window())
        .isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  void startupRejectsMissingInvalidAndShortJwtKeys() {
    var configuration = new AuthInfrastructureConfiguration();
    var environment = new MockEnvironment();
    var sessionPolicy = configuration.sessionPolicy(environment);
    for (String key : new String[] {"", "not-base64!", "c2hvcnQ="}) {
      var jwtSettings = new JwtSettings("nkc", "nkc-staff", key);
      assertThatThrownBy(
              () -> configuration.sessionTokens(jwtSettings, sessionPolicy, Clock.systemUTC()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void configurationTypesRejectInvalidSessionAndThrottleLimits() {
    assertThatThrownBy(() -> new SessionPolicy(Duration.ofHours(9), Duration.ofHours(8)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new com.ngockhanh.clinic.identity.infrastructure.session.LoginThrottleSettings(
                    0, 60, Duration.ofMinutes(15)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void accountTransactionTemplatesKeepReadAndWriteBoundaries() {
    var configuration = new AuthInfrastructureConfiguration();
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    TransactionTemplate read = (TransactionTemplate) configuration.accountReadTransaction(manager);
    TransactionTemplate snapshot =
        (TransactionTemplate) configuration.accountSnapshotTransaction(manager);
    TransactionTemplate write =
        (TransactionTemplate) configuration.accountWriteTransaction(manager);

    assertThat(read.isReadOnly()).isTrue();
    assertThat(snapshot.isReadOnly()).isTrue();
    assertThat(snapshot.getIsolationLevel())
        .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    assertThat(write.isReadOnly()).isFalse();
  }
}
