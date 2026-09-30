package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.application.command.LoginStaffCommand;
import com.ngockhanh.clinic.identity.application.command.LogoutAllStaffSessionsCommand;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.query.AuthenticateStaffSessionQuery;
import com.ngockhanh.clinic.identity.application.port.LoginThrottle;
import com.ngockhanh.clinic.identity.application.port.Passwords;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.application.usecase.AuthenticateStaffSessionUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LoginStaffUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutAllStaffSessionsUseCase;
import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;
import com.ngockhanh.clinic.identity.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import com.ngockhanh.clinic.shared.audit.AuthAudit;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

class StaffLoginFailureTest {
    final StaffAccountRepository accounts = mock(StaffAccountRepository.class);
    final AuthAudit audit = mock(AuthAudit.class);
    final TransactionOperations directTransaction = new TransactionOperations() {
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
    final LoginStaffUseCase login = new LoginStaffUseCase(accounts, audit, passwords, tokens, sessions, throttle,
            SessionAdaptersTest.settings(), Clock.fixed(now, ZoneOffset.UTC), () -> id,
            directTransaction, directTransaction, directTransaction);
    final AuthenticateStaffSessionUseCase authenticate = new AuthenticateStaffSessionUseCase(
            sessions, tokens, SessionAdaptersTest.settings(), Clock.fixed(now, ZoneOffset.UTC));
    final LogoutAllStaffSessionsUseCase logoutAll = new LogoutAllStaffSessionsUseCase(
            sessions, audit, Clock.fixed(now, ZoneOffset.UTC), directTransaction);
    final LoginStaffCommand command = new LoginStaffCommand(
            " staff ", "password", "127.0.0.1", List.of(), UUID.randomUUID());

    @BeforeEach
    void validAccount() {
        when(throttle.check(anyString(), anyString())).thenReturn(new LoginThrottle.CheckResult(true, 0));
        var role = new RoleAssignment(UUID.randomUUID(), "DOCTOR", List.of("READ"), null, null, now.minusSeconds(60), null);
        when(accounts.identify("staff")).thenReturn(user);
        when(accounts.recordLogin(user, now)).thenReturn(1);
        when(sessions.generation(user)).thenReturn(7L);
        when(accounts.find("staff", now)).thenReturn(new StaffAccount(user, UUID.randomUUID(), "staff",
                "encoded", "ACTIVE", "STAFF", true, List.of(role)));
        when(passwords.matches("password", "encoded")).thenReturn(true);
        when(tokens.issue(any())).thenReturn("signed-token");
        when(sessions.create(eq(id), any(), any(), eq(now))).thenReturn(true);
        when(sessions.touch(eq(id), any(), any(), eq(now))).thenReturn(now.plusSeconds(1800));
    }

    @Test
    void databaseFailureCompensatesAndNeverReturnsSession() {
        var failure = new IllegalStateException("database unavailable");
        doThrow(failure).when(audit).record(user, "STAFF_LOGIN", now, command.correlationId());
        assertThatThrownBy(() -> login.execute(command)).isSameAs(failure);
        verify(sessions).delete(id);
        verify(sessions, never()).touch(any(), any(), any(), any());
    }

    @Test
    void applicationMapsThrottleResultsToRateLimitFailures() {
        when(throttle.check("staff", "127.0.0.1")).thenReturn(new LoginThrottle.CheckResult(false, 45));
        assertThatThrownBy(() -> login.execute(command))
                .isInstanceOfSatisfying(AuthenticationFailure.class, failure -> {
                    assertThat(failure.type()).isEqualTo(
                            com.ngockhanh.clinic.shared.exception.ApplicationException.Type.RATE_LIMITED);
                    assertThat(failure.retryAfterSeconds()).isEqualTo(45);
                });
        verifyNoInteractions(accounts, passwords, tokens, sessions);
    }

    @Test
    void applicationRejectsDuplicateSessionCookiesAtTheEndpointSpecificStatus() {
        var duplicateCookies = new LoginStaffCommand("staff", "password", "127.0.0.1",
                List.of("old-session", "other-session"), UUID.randomUUID());
        assertThatThrownBy(() -> login.execute(duplicateCookies))
                .isInstanceOfSatisfying(AuthenticationFailure.class, failure -> assertThat(failure.type())
                        .isEqualTo(com.ngockhanh.clinic.shared.exception.ApplicationException.Type.INVALID_INPUT));
        assertThatThrownBy(() -> authenticate.execute(new AuthenticateStaffSessionQuery(List.of("first", "second"))))
                .isInstanceOfSatisfying(AuthenticationFailure.class, failure -> assertThat(failure.type())
                        .isEqualTo(com.ngockhanh.clinic.shared.exception.ApplicationException.Type.UNAUTHENTICATED));
        verifyNoInteractions(accounts, passwords, tokens, sessions);
    }

    @Test
    void failedCompensationKeepsOriginalFailureAndDoesNotReturnSession() {
        var failure = new IllegalStateException("audit commit failure");
        doThrow(failure).when(audit).record(user, "STAFF_LOGIN", now, command.correlationId());
        doThrow(new DependencyUnavailableException("Redis unavailable", new IllegalStateException("offline")))
                .when(sessions).delete(id);
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
        verify(accounts, never()).recordLogin(any(), any());
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
        assertThatThrownBy(() -> login.execute(command)).isInstanceOfSatisfying(AuthenticationFailure.class,
                failure -> assertThat(failure.type()).isEqualTo(
                        com.ngockhanh.clinic.shared.exception.ApplicationException.Type.UNAUTHENTICATED));
        verify(passwords).matches("password", null);
        verify(throttle).failed("staff");
        verify(sessions, never()).create(any(), any(), any(), any());
    }

    @Test
    void auditFailureCannotUndoSuccessfulRevocation() {
        doThrow(new IllegalStateException("audit unavailable")).when(audit)
                .record(user, "STAFF_SESSIONS_REVOKED", now, command.correlationId());
        assertThatCode(() -> logoutAll.execute(new LogoutAllStaffSessionsCommand(user, command.correlationId())))
                .doesNotThrowAnyException();
        verify(sessions).revokeAll(user);
    }

    @Test
    void redisRevocationFailureIsNotReportedAsSuccessfulLogout() {
        doThrow(new DependencyUnavailableException("Redis unavailable", new IllegalStateException("offline")))
                .when(sessions).revokeAll(user);
        assertThatThrownBy(() -> logoutAll.execute(new LogoutAllStaffSessionsCommand(user, command.correlationId())))
                .isInstanceOfSatisfying(AuthenticationFailure.class,
                        failure -> assertThat(failure.type()).isEqualTo(
                                com.ngockhanh.clinic.shared.exception.ApplicationException.Type.DEPENDENCY_UNAVAILABLE));
        verify(audit, never()).record(any(), anyString(), any(), any());
    }

    @Test
    void expiredAssignmentsAreRemovedFromSnapshotAndLastRoleRevokesSession() {
        var active = new RoleAssignment(UUID.randomUUID(), "ACTIVE_ROLE", List.of(), null, null, now, now.plusSeconds(5));
        var expired = new RoleAssignment(UUID.randomUUID(), "EXPIRED_ROLE", List.of(), null, null, now.minusSeconds(60), now);
        var claims = new SessionTokens.Claims(user, UUID.randomUUID(), "staff", UUID.randomUUID(), now,
                now.plusSeconds(28800), List.of(active, expired));
        var stored = new SessionStore.Stored(user, "signed-token", 7, claims.expiresAt());
        when(sessions.find(id)).thenReturn(stored);
        when(tokens.verify("signed-token")).thenReturn(Optional.of(claims));
        assertThat(authenticate.execute(new AuthenticateStaffSessionQuery(List.of(id))).roleAssignments())
                .extracting(r -> r.roleCode()).containsExactly("ACTIVE_ROLE");
        var later = new AuthenticateStaffSessionUseCase(
                sessions, tokens, SessionAdaptersTest.settings(), Clock.fixed(now.plusSeconds(5), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.execute(new AuthenticateStaffSessionQuery(List.of(id))))
                .isInstanceOf(AuthenticationFailure.class);
        verify(sessions).delete(id);
    }
}
