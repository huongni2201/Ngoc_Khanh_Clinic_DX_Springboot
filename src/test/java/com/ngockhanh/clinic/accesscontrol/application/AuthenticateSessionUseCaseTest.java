package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.command.LogoutCommand;
import com.ngockhanh.clinic.accesscontrol.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.accesscontrol.application.port.SessionSnapshot;
import com.ngockhanh.clinic.accesscontrol.application.port.SessionStore;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LogoutUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SessionPolicy;
import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class AuthenticateSessionUseCaseTest {
  private final Instant now = Instant.parse("2026-10-06T01:00:00Z");
  private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
  private final SessionStore sessions = mock(SessionStore.class);
  private final SessionPolicy policy =
      new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(8));
  private final AuthenticateSessionUseCase authenticate =
      new AuthenticateSessionUseCase(sessions, policy, clock);
  private final UUID accountId = UUID.randomUUID();

  private SessionSnapshot snapshot(Instant absoluteExpiresAt) {
    return SessionSnapshot.builder()
        .accountId(accountId)
        .accountType("STAFF")
        .staffMemberId(UUID.randomUUID())
        .username("staff")
        .roles(
            List.of(
                SessionSnapshot.Role.builder()
                    .roleId(UUID.randomUUID())
                    .roleCode("DOCTOR")
                    .permissions(List.of("ORGANIZATION_READ"))
                    .build()))
        .createdAt(now.minusSeconds(60))
        .absoluteExpiresAt(absoluteExpiresAt)
        .build();
  }

  @Test
  void liveSessionIsExtendedByTheIdleTimeout() {
    when(sessions.find("sid")).thenReturn(Optional.of(snapshot(now.plus(Duration.ofHours(7)))));
    when(sessions.touch("sid", Duration.ofMinutes(30))).thenReturn(true);

    var principal = authenticate.execute("sid");

    assertThat(principal.userId()).isEqualTo(accountId);
    assertThat(principal.idleExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
    assertThat(principal.roleAssignments().getFirst().roleCode()).isEqualTo("DOCTOR");
  }

  @Test
  void extensionNeverPassesTheAbsoluteExpiry() {
    when(sessions.find("sid")).thenReturn(Optional.of(snapshot(now.plus(Duration.ofMinutes(10)))));
    when(sessions.touch("sid", Duration.ofMinutes(10))).thenReturn(true);

    assertThat(authenticate.execute("sid").idleExpiresAt())
        .isEqualTo(now.plus(Duration.ofMinutes(10)));
  }

  @Test
  void missingSessionIsUnauthenticated() {
    when(sessions.find("sid")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> authenticate.execute("sid")).isInstanceOf(AuthenticationFailure.class);
    assertThatThrownBy(() -> authenticate.execute(null)).isInstanceOf(AuthenticationFailure.class);
  }

  @Test
  void sessionPastItsAbsoluteExpiryIsDeleted() {
    when(sessions.find("sid")).thenReturn(Optional.of(snapshot(now)));
    assertThatThrownBy(() -> authenticate.execute("sid")).isInstanceOf(AuthenticationFailure.class);
    verify(sessions).delete("sid");
    verify(sessions, never()).touch(any(), any());
  }

  @Test
  void sessionThatExpiresBeforeTouchIsUnauthenticated() {
    when(sessions.find("sid")).thenReturn(Optional.of(snapshot(now.plus(Duration.ofHours(1)))));
    when(sessions.touch("sid", Duration.ofMinutes(30))).thenReturn(false);
    assertThatThrownBy(() -> authenticate.execute("sid")).isInstanceOf(AuthenticationFailure.class);
  }

  @Test
  void logoutEndsTheSessionAndAuditsOnlyLiveSessions() {
    AuditWriter audit = mock(AuditWriter.class);
    var logout =
        new LogoutUseCase(sessions, audit, TransactionOperations.withoutTransaction(), clock);
    UUID correlation = UUID.randomUUID();
    when(sessions.find("sid")).thenReturn(Optional.of(snapshot(now.plus(Duration.ofHours(1)))));

    logout.execute(LogoutCommand.builder().sessionId("sid").correlationId(correlation).build());
    logout.execute(LogoutCommand.builder().sessionId(null).correlationId(correlation).build());

    verify(sessions).delete("sid");
    verify(audit).record(accountId, "ACCOUNT_LOGOUT", now, correlation);
  }
}
