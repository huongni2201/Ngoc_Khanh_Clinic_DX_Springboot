package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.command.LoginCommand;
import com.ngockhanh.clinic.accesscontrol.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionSnapshot;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionStore;
import com.ngockhanh.clinic.accesscontrol.application.response.LoginResult;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LoginUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.entity.UserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.enums.StaffMemberStatus;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleGrant;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SessionPolicy;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionOperations;

class LoginUseCaseTest {
  private static final String PASSWORD = "Mật khẩu-01";
  private static final String HASH = "synthetic-password-hash";
  private final PasswordEncoder passwords = mock(PasswordEncoder.class);

  private final Instant now = Instant.parse("2026-10-06T01:00:00Z");
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final SessionStore sessions = mock(SessionStore.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final UUID accountId = UUID.randomUUID();
  private final UUID correlation = UUID.randomUUID();
  private final LoginUseCase login =
      new LoginUseCase(
          accounts,
          passwords,
          sessions,
          audit,
          TransactionOperations.withoutTransaction(),
          new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(8)),
          Clock.fixed(now, ZoneOffset.UTC));

  @BeforeEach
  void passwordVerificationIsAnExternalCollaborator() {
    when(passwords.matches(any(), eq(HASH)))
        .thenAnswer(call -> PASSWORD.contentEquals((CharSequence) call.getArgument(0)));
  }

  private UserAccount staff(AccountStatus status) {
    return new UserAccount(
        accountId,
        AccountType.STAFF,
        "staff",
        HASH,
        status,
        UUID.randomUUID(),
        StaffMemberStatus.ACTIVE,
        null,
        List.of(new RoleGrant(UUID.randomUUID(), "DOCTOR", List.of("ORGANIZATION_READ"))));
  }

  private LoginCommand command(String password, String previousSessionId) {
    return LoginCommand.builder()
        .username("staff")
        .password(password)
        .previousSessionId(previousSessionId)
        .correlationId(correlation)
        .build();
  }

  @Test
  void successAuditsBeforeStoringSessionAndReturnsPrincipal() {
    when(accounts.findByUsername("staff")).thenReturn(Optional.of(staff(AccountStatus.ACTIVE)));

    LoginResult result = login.execute(command(PASSWORD, null));

    assertThat(result.sessionId()).hasSize(43).doesNotContain("=", "+", "/");
    assertThat(result.principal().userId()).isEqualTo(accountId);
    assertThat(result.principal().principalType()).isEqualTo("STAFF");
    assertThat(result.principal().roleAssignments().getFirst().permissions())
        .containsExactly("ORGANIZATION_READ");
    assertThat(result.principal().absoluteExpiresAt()).isEqualTo(now.plus(Duration.ofHours(8)));
    assertThat(result.principal().idleExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));

    var order = inOrder(audit, sessions);
    order.verify(audit).record(accountId, "ACCOUNT_LOGIN", now, correlation);
    ArgumentCaptor<SessionSnapshot> snapshot = ArgumentCaptor.forClass(SessionSnapshot.class);
    order
        .verify(sessions)
        .create(eq(result.sessionId()), snapshot.capture(), eq(Duration.ofMinutes(30)));
    assertThat(snapshot.getValue().absoluteExpiresAt()).isEqualTo(now.plus(Duration.ofHours(8)));
  }

  @Test
  void unknownUserWrongPasswordAndInactiveAccountFailIdentically() {
    when(accounts.findByUsername("staff")).thenReturn(Optional.empty());
    String unknown = failureMessage(command(PASSWORD, null));

    when(accounts.findByUsername("staff")).thenReturn(Optional.of(staff(AccountStatus.ACTIVE)));
    String wrongPassword = failureMessage(command("wrong", null));

    when(accounts.findByUsername("staff")).thenReturn(Optional.of(staff(AccountStatus.DISABLED)));
    String inactive = failureMessage(command(PASSWORD, null));

    assertThat(unknown).isEqualTo(wrongPassword).isEqualTo(inactive);
    verify(sessions, never()).create(anyString(), any(), any());
  }

  @Test
  void failedSignInOfExistingAccountIsAuditedWithTheAccountAsActor() {
    when(accounts.findByUsername("staff")).thenReturn(Optional.of(staff(AccountStatus.ACTIVE)));
    failureMessage(command("wrong", null));
    verify(audit).record(accountId, "ACCOUNT_LOGIN_FAILED", now, correlation);
  }

  @Test
  void vietnamesePasswordLongerThan72BytesIsAnInvalidSignIn() {
    when(accounts.findByUsername("staff")).thenReturn(Optional.of(staff(AccountStatus.ACTIVE)));
    assertThatThrownBy(() -> login.execute(command("ậA".repeat(19), null)))
        .isInstanceOf(AuthenticationFailure.class);
  }

  @Test
  void previousBrowserSessionIsEndedBeforeSignIn() {
    when(accounts.findByUsername("staff")).thenReturn(Optional.of(staff(AccountStatus.ACTIVE)));
    login.execute(command(PASSWORD, "old-session"));
    verify(sessions).delete("old-session");
  }

  @Test
  void auditFailurePreventsSessionCreation() {
    when(accounts.findByUsername("staff")).thenReturn(Optional.of(staff(AccountStatus.ACTIVE)));
    doThrow(new IllegalStateException("Audit not saved"))
        .when(audit)
        .record(accountId, "ACCOUNT_LOGIN", now, correlation);

    assertThatThrownBy(() -> login.execute(command(PASSWORD, null)))
        .isInstanceOf(IllegalStateException.class);
    verify(sessions, never()).create(anyString(), any(), any());
  }

  @Test
  void patientSessionHasNoStaffIdentity() {
    UUID patientId = UUID.randomUUID();
    when(accounts.findByUsername("staff"))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    accountId,
                    AccountType.PATIENT,
                    "staff",
                    HASH,
                    AccountStatus.ACTIVE,
                    null,
                    null,
                    patientId,
                    List.of())));

    LoginResult result = login.execute(command(PASSWORD, null));

    assertThat(result.principal().principalType()).isEqualTo("PATIENT");
    assertThat(result.principal().patientId()).isEqualTo(patientId);
    assertThat(result.principal().staffId()).isNull();
    assertThat(result.principal().roleAssignments()).isEmpty();
  }

  private String failureMessage(LoginCommand command) {
    try {
      login.execute(command);
    } catch (AuthenticationFailure failure) {
      return failure.type() + ":" + failure.getMessage();
    }
    throw new AssertionError("Sign-in was expected to fail");
  }
}
