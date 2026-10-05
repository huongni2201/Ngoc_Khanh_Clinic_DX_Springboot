package com.ngockhanh.clinic.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.AuthAudit;
import com.ngockhanh.clinic.identity.application.command.LoginCommand;
import com.ngockhanh.clinic.identity.application.command.LogoutAllSessionsCommand;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.LoginThrottle;
import com.ngockhanh.clinic.identity.application.port.Passwords;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.application.query.AuthenticateSessionQuery;
import com.ngockhanh.clinic.identity.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LoginUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutAllSessionsUseCase;
import com.ngockhanh.clinic.identity.domain.entity.UserAccount;
import com.ngockhanh.clinic.identity.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class StaffLoginFailureTest {
  final UserAccountRepository accounts = mock(UserAccountRepository.class);
  final AuthAudit audit = mock(AuthAudit.class);
  final TransactionOperations directTransaction =
      new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
          return action.doInTransaction(null);
        }
      };
  final Passwords passwords = mock(Passwords.class);
  final SessionTokens tokens = mock(SessionTokens.class);
  final SessionStore sessions = mock(SessionStore.class);
  final LoginThrottle throttle = mock(LoginThrottle.class);
  final UUID user = UUID.randomUUID();
  final Instant now = Instant.parse("2026-09-28T00:00:00Z");
  final String id = "A".repeat(43);
  final LoginUseCase login =
      new LoginUseCase(
          accounts,
          audit,
          passwords,
          tokens,
          sessions,
          throttle,
          SessionAdaptersTest.settings(),
          Clock.fixed(now, ZoneOffset.UTC),
          () -> id,
          directTransaction,
          directTransaction,
          directTransaction);
  final AuthenticateSessionUseCase authenticate =
      new AuthenticateSessionUseCase(
          sessions, tokens, SessionAdaptersTest.settings(), Clock.fixed(now, ZoneOffset.UTC));
  final LogoutAllSessionsUseCase logoutAll =
      new LogoutAllSessionsUseCase(
          sessions, audit, Clock.fixed(now, ZoneOffset.UTC), directTransaction);
  final LoginCommand command =
      LoginCommand.builder()
          .username(" staff ")
          .password("password")
          .clientIp("127.0.0.1")
          .sessionIds(List.of())
          .correlationId(UUID.randomUUID())
          .build();

  @BeforeEach
  void validAccount() {
    when(throttle.check(anyString(), anyString()))
        .thenReturn(new LoginThrottle.CheckResult(true, 0));
    var role =
        new RoleAssignment(
            UUID.randomUUID(), "DOCTOR", List.of("READ"), UUID.randomUUID(), now.minusSeconds(60));
    when(accounts.identify("staff")).thenReturn(user);
    when(accounts.lockEligibleAccount(user)).thenReturn(true);
    when(sessions.generation(user)).thenReturn(7L);
    when(accounts.find("staff", now))
        .thenReturn(
            new UserAccount(
                user,
                UUID.randomUUID(),
                null,
                "staff",
                "encoded",
                "ACTIVE",
                "STAFF",
                "ACTIVE",
                List.of(role)));
    when(passwords.matches("password", "encoded")).thenReturn(true);
    when(tokens.issue(any())).thenReturn("signed-token");
    when(sessions.create(eq(id), any(), any(), eq(now))).thenReturn(true);
    when(sessions.touch(eq(id), any(), any(), eq(now))).thenReturn(now.plusSeconds(1800));
  }

  @Test
  void rejectsUsernameBeyondCleanSlateAccountLimitBeforeReadingCredentials() {
    var oversized =
        LoginCommand.builder()
            .username("x".repeat(151))
            .password("password")
            .clientIp("127.0.0.1")
            .sessionIds(List.of())
            .correlationId(UUID.randomUUID())
            .build();
    assertThatThrownBy(() -> login.execute(oversized))
        .isInstanceOfSatisfying(
            AuthenticationFailure.class,
            failure ->
                assertThat(failure.type())
                    .isEqualTo(
                        com.ngockhanh.clinic.shared.exception.ApplicationException.Type
                            .INVALID_INPUT));
    verifyNoInteractions(accounts, passwords, tokens, sessions);
  }

  @Test
  void databaseFailureCompensatesAndNeverReturnsSession() {
    var failure = new IllegalStateException("database unavailable");
    doThrow(failure).when(audit).record(user, "ACCOUNT_LOGIN", now, command.correlationId());
    assertThatThrownBy(() -> login.execute(command)).isSameAs(failure);
    verify(sessions).delete(id);
    verify(sessions, never()).touch(any(), any(), any(), any());
  }

  @Test
  void applicationMapsThrottleResultsToRateLimitFailures() {
    when(throttle.check("staff", "127.0.0.1")).thenReturn(new LoginThrottle.CheckResult(false, 45));
    assertThatThrownBy(() -> login.execute(command))
        .isInstanceOfSatisfying(
            AuthenticationFailure.class,
            failure -> {
              assertThat(failure.type())
                  .isEqualTo(
                      com.ngockhanh.clinic.shared.exception.ApplicationException.Type.RATE_LIMITED);
              assertThat(failure.retryAfterSeconds()).isEqualTo(45);
            });
    verifyNoInteractions(accounts, passwords, tokens, sessions);
  }

  @Test
  void applicationRejectsDuplicateSessionCookiesAtTheEndpointSpecificStatus() {
    var duplicateCookies =
        LoginCommand.builder()
            .username("staff")
            .password("password")
            .clientIp("127.0.0.1")
            .sessionIds(List.of("old-session", "other-session"))
            .correlationId(UUID.randomUUID())
            .build();
    assertThatThrownBy(() -> login.execute(duplicateCookies))
        .isInstanceOfSatisfying(
            AuthenticationFailure.class,
            failure ->
                assertThat(failure.type())
                    .isEqualTo(
                        com.ngockhanh.clinic.shared.exception.ApplicationException.Type
                            .INVALID_INPUT));
    assertThatThrownBy(
            () ->
                authenticate.execute(
                    AuthenticateSessionQuery.builder()
                        .sessionIds(List.of("first", "second"))
                        .build()))
        .isInstanceOfSatisfying(
            AuthenticationFailure.class,
            failure ->
                assertThat(failure.type())
                    .isEqualTo(
                        com.ngockhanh.clinic.shared.exception.ApplicationException.Type
                            .UNAUTHENTICATED));
    verifyNoInteractions(accounts, passwords, tokens, sessions);
  }

  @Test
  void failedCompensationKeepsOriginalFailureAndDoesNotReturnSession() {
    var failure = new IllegalStateException("audit commit failure");
    doThrow(failure).when(audit).record(user, "ACCOUNT_LOGIN", now, command.correlationId());
    doThrow(
            new DependencyUnavailableException(
                "Redis unavailable", new IllegalStateException("offline")))
        .when(sessions)
        .delete(id);
    assertThatThrownBy(() -> login.execute(command)).isSameAs(failure);
  }

  @Test
  void generationIsReadBeforeCredentialSnapshotAndStaleCreationIsDenied() {
    when(sessions.create(eq(id), any(), any(), eq(now))).thenReturn(false);
    assertThatThrownBy(() -> login.execute(command)).isInstanceOf(AuthenticationFailure.class);
    var ordered = inOrder(accounts, sessions);
    ordered.verify(accounts).identify("staff");
    ordered.verify(sessions).generation(user);
    ordered.verify(accounts).find("staff", now);
    verify(accounts, never()).lockEligibleAccount(any());
  }

  @Test
  void revokedDuringDatabaseCommitCannotBeIssued() {
    when(sessions.touch(eq(id), any(), any(), eq(now))).thenReturn(null);
    assertThatThrownBy(() -> login.execute(command)).isInstanceOf(AuthenticationFailure.class);
    verify(sessions).delete(id);
  }

  @Test
  void unknownUserStillVerifiesPasswordAndReturnsGenericUnauthorized() {
    when(accounts.identify("staff")).thenReturn(null);
    when(accounts.find("staff", now)).thenReturn(null);
    assertThatThrownBy(() -> login.execute(command))
        .isInstanceOfSatisfying(
            AuthenticationFailure.class,
            failure ->
                assertThat(failure.type())
                    .isEqualTo(
                        com.ngockhanh.clinic.shared.exception.ApplicationException.Type
                            .UNAUTHENTICATED));
    verify(passwords).matches("password", null);
    verify(throttle).failed("staff");
    verify(sessions, never()).create(any(), any(), any(), any());
  }

  @Test
  void auditFailureCannotUndoSuccessfulRevocation() {
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(user, "ACCOUNT_SESSIONS_REVOKED", now, command.correlationId());
    assertThatCode(
            () ->
                logoutAll.execute(
                    LogoutAllSessionsCommand.builder()
                        .userId(user)
                        .correlationId(command.correlationId())
                        .build()))
        .doesNotThrowAnyException();
    verify(sessions).revokeAll(user);
  }

  @Test
  void redisRevocationFailureIsNotReportedAsSuccessfulLogout() {
    doThrow(
            new DependencyUnavailableException(
                "Redis unavailable", new IllegalStateException("offline")))
        .when(sessions)
        .revokeAll(user);
    assertThatThrownBy(
            () ->
                logoutAll.execute(
                    LogoutAllSessionsCommand.builder()
                        .userId(user)
                        .correlationId(command.correlationId())
                        .build()))
        .isInstanceOfSatisfying(
            AuthenticationFailure.class,
            failure ->
                assertThat(failure.type())
                    .isEqualTo(
                        com.ngockhanh.clinic.shared.exception.ApplicationException.Type
                            .DEPENDENCY_UNAVAILABLE));
    verify(audit, never()).record(any(), anyString(), any(), any());
  }

  @Test
  void accountRoleGrantsRemainInTheSessionSnapshotWithoutLegacyExpiry() {
    var role = new RoleAssignment(UUID.randomUUID(), "DOCTOR", List.of("READ"), user, now);
    var claims =
        new SessionTokens.Claims(
            user,
            UUID.randomUUID(),
            null,
            "staff",
            "STAFF",
            UUID.randomUUID(),
            now,
            now.plusSeconds(28800),
            List.of(role));
    var stored = new SessionStore.Stored(user, "signed-token", 7, claims.expiresAt());
    when(sessions.find(id)).thenReturn(stored);
    when(tokens.verify("signed-token")).thenReturn(Optional.of(claims));
    var later =
        new AuthenticateSessionUseCase(
            sessions,
            tokens,
            SessionAdaptersTest.settings(),
            Clock.fixed(now.plusSeconds(5), ZoneOffset.UTC));
    when(sessions.touch(eq(id), any(), any(), eq(now.plusSeconds(5))))
        .thenReturn(now.plusSeconds(1805));
    assertThat(
            later
                .execute(AuthenticateSessionQuery.builder().sessionIds(List.of(id)).build())
                .roleAssignments())
        .extracting(r -> r.roleCode())
        .containsExactly("DOCTOR");
  }

  @Test
  void eligibilityChangeBeforeAuditPreventsSessionIssuance() {
    when(accounts.lockEligibleAccount(user)).thenReturn(false);
    assertThatThrownBy(() -> login.execute(command)).isInstanceOf(AuthenticationFailure.class);
    verify(audit, never()).record(any(), anyString(), any(), any());
    verify(sessions).delete(id);
  }

  @Test
  void patientWithoutRolesReceivesItsOwnIdentityAndNoStaffIdentity() {
    UUID patient = UUID.randomUUID();
    when(accounts.find("staff", now))
        .thenReturn(
            new UserAccount(
                user, null, patient, "patient", "encoded", "ACTIVE", "PATIENT", null, List.of()));
    var result = login.execute(command).response();
    assertThat(result.accountType()).isEqualTo("PATIENT");
    assertThat(result.patientId()).isEqualTo(patient);
    assertThat(result.staffMemberId()).isNull();
    assertThat(result.roleAssignments()).isEmpty();
    verify(tokens)
        .issue(
            org.mockito.ArgumentMatchers.argThat(
                claims ->
                    claims.principalType().equals("PATIENT")
                        && patient.equals(claims.patientId())
                        && claims.staffId() == null
                        && claims.roles().isEmpty()));
  }

  @Test
  void activeStaffWithoutRolesCanLogIn() {
    when(accounts.find("staff", now))
        .thenReturn(
            new UserAccount(
                user,
                UUID.randomUUID(),
                null,
                "staff",
                "encoded",
                "ACTIVE",
                "STAFF",
                "ACTIVE",
                List.of()));
    assertThat(login.execute(command).response().roleAssignments()).isEmpty();
  }
}
