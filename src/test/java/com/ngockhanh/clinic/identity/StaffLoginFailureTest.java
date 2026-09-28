package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.application.command.StaffLoginCommand;
import com.ngockhanh.clinic.identity.application.port.*;
import com.ngockhanh.clinic.identity.application.usecase.*;
import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;
import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffLoginFailureTest {
    final AccountTransactions accounts = mock(AccountTransactions.class);
    final Passwords passwords = mock(Passwords.class);
    final SessionTokens tokens = mock(SessionTokens.class);
    final SessionStore sessions = mock(SessionStore.class);
    final LoginThrottle throttle = mock(LoginThrottle.class);
    final UUID user = UUID.randomUUID();
    final Instant now = Instant.parse("2026-09-28T00:00:00Z");
    final String id = "A".repeat(43);
    final StaffAuthentication auth = new StaffAuthentication(accounts, passwords, tokens, sessions,
            throttle, SessionAdaptersTest.settings(), Clock.fixed(now, ZoneOffset.UTC), () -> id);
    final StaffLoginCommand command = new StaffLoginCommand(" staff ", "password", "127.0.0.1", null, UUID.randomUUID());

    @BeforeEach void validAccount() {
        var role = new RoleAssignment(UUID.randomUUID(), "DOCTOR", List.of("READ"), null, null, now.minusSeconds(60), null);
        when(accounts.identify("staff")).thenReturn(user);
        when(sessions.generation(user)).thenReturn(7L);
        when(accounts.load("staff", now)).thenReturn(new StaffAccount(user, UUID.randomUUID(), "staff",
                "encoded", "ACTIVE", "STAFF", true, List.of(role)));
        when(passwords.matches("password", "encoded")).thenReturn(true);
        when(tokens.issue(any())).thenReturn("signed-token");
        when(sessions.create(eq(id), any(), any(), eq(now))).thenReturn(true);
        when(sessions.touch(eq(id), any(), any(), eq(now))).thenReturn(now.plusSeconds(1800));
    }

    @Test void databaseFailureCompensatesAndNeverReturnsSession() {
        var failure = new IllegalStateException("database unavailable");
        doThrow(failure).when(accounts).loginRecorded(user, now, command.correlationId());
        assertThatThrownBy(() -> auth.login(command)).isSameAs(failure);
        verify(sessions).delete(id);
        verify(sessions, never()).touch(any(), any(), any(), any());
    }

    @Test void failedCompensationKeepsOriginalFailureAndDoesNotReturnSession() {
        var failure = new IllegalStateException("audit commit failure");
        doThrow(failure).when(accounts).loginRecorded(user, now, command.correlationId());
        doThrow(AuthenticationFailure.unavailable()).when(sessions).delete(id);
        assertThatThrownBy(() -> auth.login(command)).isSameAs(failure);
    }

    @Test void generationIsReadBeforeCredentialSnapshotAndStaleCreationIsDenied() {
        when(sessions.create(eq(id), any(), any(), eq(now))).thenReturn(false);
        assertThatThrownBy(() -> auth.login(command)).isInstanceOf(AuthenticationFailure.class);
        var ordered = inOrder(accounts, sessions);
        ordered.verify(accounts).identify("staff");
        ordered.verify(sessions).generation(user);
        ordered.verify(accounts).load("staff", now);
        verify(accounts, never()).loginRecorded(any(), any(), any());
    }

    @Test void revokedDuringDatabaseCommitCannotBeIssued() {
        when(sessions.touch(eq(id), any(), any(), eq(now))).thenReturn(null);
        assertThatThrownBy(() -> auth.login(command)).isInstanceOf(AuthenticationFailure.class);
        verify(sessions).delete(id);
    }

    @Test void unknownUserStillVerifiesPasswordAndReturnsGenericUnauthorized() {
        when(accounts.identify("staff")).thenReturn(null);
        when(accounts.load("staff", now)).thenReturn(null);
        assertThatThrownBy(() -> auth.login(command)).isInstanceOfSatisfying(AuthenticationFailure.class,
                failure -> assertThat(failure.status()).isEqualTo(401));
        verify(passwords).matches("password", null);
        verify(throttle).failed("staff");
        verify(sessions, never()).create(any(), any(), any(), any());
    }

    @Test void auditFailureCannotUndoSuccessfulRevocation() {
        doThrow(new IllegalStateException("audit unavailable")).when(accounts).revoked(user, true, now, command.correlationId());
        assertThatCode(() -> auth.logoutAll(user, command.correlationId())).doesNotThrowAnyException();
        verify(sessions).revokeAll(user);
    }

    @Test void redisRevocationFailureIsNotReportedAsSuccessfulLogout() {
        doThrow(AuthenticationFailure.unavailable()).when(sessions).revokeAll(user);
        assertThatThrownBy(() -> auth.logoutAll(user, command.correlationId()))
                .isInstanceOfSatisfying(AuthenticationFailure.class, failure -> assertThat(failure.status()).isEqualTo(503));
        verify(accounts, never()).revoked(any(), anyBoolean(), any(), any());
    }

    @Test void expiredAssignmentsAreRemovedFromSnapshotAndLastRoleRevokesSession() {
        var active = new RoleAssignment(UUID.randomUUID(), "ACTIVE_ROLE", List.of(), null, null, now, now.plusSeconds(5));
        var expired = new RoleAssignment(UUID.randomUUID(), "EXPIRED_ROLE", List.of(), null, null, now.minusSeconds(60), now);
        var claims = new SessionTokens.Claims(user, UUID.randomUUID(), "staff", UUID.randomUUID(), now,
                now.plusSeconds(28800), List.of(active, expired));
        var stored = new SessionStore.Stored(user, "signed-token", 7, claims.expiresAt());
        when(sessions.find(id)).thenReturn(stored);
        when(tokens.verify("signed-token")).thenReturn(claims);
        assertThat(auth.authenticate(id).roleAssignments()).extracting(r -> r.roleCode()).containsExactly("ACTIVE_ROLE");
        var later = new StaffAuthentication(accounts, passwords, tokens, sessions, throttle,
                SessionAdaptersTest.settings(), Clock.fixed(now.plusSeconds(5), ZoneOffset.UTC), () -> id);
        assertThatThrownBy(() -> later.authenticate(id)).isInstanceOf(AuthenticationFailure.class);
        verify(sessions).delete(id);
    }
}
