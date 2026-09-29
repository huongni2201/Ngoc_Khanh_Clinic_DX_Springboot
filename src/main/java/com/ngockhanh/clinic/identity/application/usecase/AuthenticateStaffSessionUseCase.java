package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.AuthSettings;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.application.query.AuthenticateStaffSessionQuery;
import com.ngockhanh.clinic.identity.application.query.access.StaffPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AuthenticateStaffSessionUseCase {
  private final SessionStore sessions;
  private final SessionTokens tokens;
  private final AuthSettings settings;
  private final Clock clock;

  public StaffPrincipal execute(AuthenticateStaffSessionQuery query) {
    String sessionId = StaffSessionSupport.singleCookie(query.sessionIds(), true);
    if (sessionId == null) {
      throw AuthenticationFailure.invalid();
    }
    SessionStore.Stored stored = StaffSessionSupport.sessionDependency(() -> sessions.find(sessionId));
    if (stored == null) {
      throw AuthenticationFailure.invalid();
    }

    SessionTokens.Claims claims = tokens.verify(stored.jwt()).orElse(null);
    if (claims == null) {
      rejectSession(sessionId);
    }
    var now = clock.instant();
    if (!claims.userId().equals(stored.userId()) || !claims.expiresAt().equals(stored.absoluteExpiresAt())
        || claims.roles().stream().noneMatch(role -> role.effectiveAt(now))) {
      rejectSession(sessionId);
    }

    var idleDeadline = StaffSessionSupport.sessionDependency(
        () -> sessions.touch(sessionId, stored, settings.idleTimeout(), clock.instant()));
    if (idleDeadline == null) {
      throw AuthenticationFailure.invalid();
    }
    return StaffSessionSupport.principal(claims, idleDeadline, clock.instant());
  }

  private void rejectSession(String sessionId) {
    StaffSessionSupport.sessionDependency(() -> sessions.delete(sessionId));
    throw AuthenticationFailure.invalid();
  }
}
