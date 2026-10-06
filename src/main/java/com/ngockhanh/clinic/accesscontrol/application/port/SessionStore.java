package com.ngockhanh.clinic.accesscontrol.application.port;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side session storage keyed by the opaque session ID from the session cookie.
 *
 * <p>Implementations throw {@link
 * com.ngockhanh.clinic.shared.exception.DependencyUnavailableException} when the store cannot be
 * reached.
 */
public interface SessionStore {
  /**
   * Stores a new session that expires after {@code ttl} unless touched.
   *
   * @param sessionId opaque session ID
   * @param snapshot session state
   * @param ttl initial time-to-live
   */
  void create(String sessionId, SessionSnapshot snapshot, Duration ttl);

  /**
   * Finds a live session.
   *
   * @param sessionId opaque session ID
   * @return the session state, or empty when it does not exist or has expired
   */
  Optional<SessionSnapshot> find(String sessionId);

  /**
   * Resets the session's time-to-live.
   *
   * @param sessionId opaque session ID
   * @param ttl new time-to-live
   * @return false when the session no longer exists
   */
  Boolean touch(String sessionId, Duration ttl);

  /**
   * Ends a session; does nothing when it does not exist.
   *
   * @param sessionId opaque session ID
   */
  void delete(String sessionId);

  /**
   * Ends every session of an account, for deactivation or permission changes (BR-05).
   *
   * @param accountId account whose sessions end
   */
  void revokeAll(UUID accountId);
}
