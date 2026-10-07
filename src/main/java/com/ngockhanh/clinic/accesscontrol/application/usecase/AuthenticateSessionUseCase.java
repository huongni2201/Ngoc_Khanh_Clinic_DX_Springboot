package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionSnapshot;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionStore;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SessionPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Restores the authenticated principal from a session cookie on each request (ADR-0014). */
@Service
@RequiredArgsConstructor
public class AuthenticateSessionUseCase {
  private final SessionStore sessions;
  private final SessionPolicy policy;
  private final Clock clock;

  /**
   * Validates the session and extends it by the idle timeout, never beyond its absolute expiry.
   * Permissions are those captured at sign-in.
   *
   * @param sessionId opaque session ID from the session cookie
   * @return the principal of the session
   * @throws AuthenticationFailure if the session is missing, expired or ended meanwhile
   * @throws com.ngockhanh.clinic.shared.exception.DependencyUnavailableException if the session
   *     store is unavailable
   */
  public UserPrincipal execute(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) throw AuthenticationFailure.unauthenticated();
    SessionSnapshot snapshot =
        sessions.find(sessionId).orElseThrow(AuthenticationFailure::unauthenticated);

    Instant now = clock.instant();
    Duration remaining = Duration.between(now, snapshot.absoluteExpiresAt());
    if (!remaining.isPositive()) {
      sessions.delete(sessionId);
      throw AuthenticationFailure.unauthenticated();
    }
    Duration ttl = policy.ttl(remaining);
    if (!Boolean.TRUE.equals(sessions.touch(sessionId, ttl)))
      throw AuthenticationFailure.unauthenticated();
    return snapshot.toPrincipal(now.plus(ttl));
  }
}
