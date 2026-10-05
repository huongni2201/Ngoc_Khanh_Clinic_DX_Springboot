package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.application.query.AuthenticateSessionQuery;
import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import com.ngockhanh.clinic.identity.domain.valueobject.SessionPolicy;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticateSessionUseCase {
  private final SessionStore sessions;
  private final SessionTokens tokens;
  private final SessionPolicy sessionPolicy;
  private final Clock clock;

  public UserPrincipal execute(AuthenticateSessionQuery query) {
    String sessionId = singleSessionCookie(query.sessionIds());
    if (sessionId == null) {
      throw AuthenticationFailure.invalid();
    }
    SessionStore.Stored stored = sessionDependency(() -> sessions.find(sessionId));
    if (stored == null) {
      throw AuthenticationFailure.invalid();
    }

    SessionTokens.Claims claims = tokens.verify(stored.jwt()).orElse(null);
    if (claims == null) {
      rejectSession(sessionId);
    }
    if (!claims.userId().equals(stored.userId())
        || !claims.expiresAt().equals(stored.absoluteExpiresAt())) {
      rejectSession(sessionId);
    }

    var idleDeadline =
        sessionDependency(
            () -> sessions.touch(sessionId, stored, sessionPolicy.idleTimeout(), clock.instant()));
    if (idleDeadline == null) {
      throw AuthenticationFailure.invalid();
    }
    UserPrincipal principal =
        UserPrincipal.from(
            claims.userId(),
            claims.staffId(),
            claims.patientId(),
            claims.username(),
            claims.principalType(),
            claims.roles(),
            idleDeadline,
            claims.expiresAt());
    log.debug("Authenticated user session userId={}", principal.userId());
    return principal;
  }

  private void rejectSession(String sessionId) {
    sessionDependency(() -> sessions.delete(sessionId));
    throw AuthenticationFailure.invalid();
  }

  private String singleSessionCookie(List<String> sessionIds) {
    if (sessionIds.size() > 1) {
      throw AuthenticationFailure.invalid();
    }
    return sessionIds.isEmpty() ? null : sessionIds.get(0);
  }

  private <T> T sessionDependency(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DependencyUnavailableException unavailable) {
      throw AuthenticationFailure.unavailable(unavailable);
    }
  }

  private void sessionDependency(Runnable operation) {
    try {
      operation.run();
    } catch (DependencyUnavailableException unavailable) {
      throw AuthenticationFailure.unavailable(unavailable);
    }
  }
}
