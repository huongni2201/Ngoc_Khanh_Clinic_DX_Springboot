package com.ngockhanh.clinic.accesscontrol.infrastructure.session;

import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionSnapshot;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionStore;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis session storage. Keys use the SHA-256 of the session ID so that a Redis dump does not
 * expose usable cookies. Each account also has a set of its session hashes for {@link
 * #revokeAll(UUID)}.
 */
@Repository
@RequiredArgsConstructor
public class RedisSessionStore implements SessionStore {
  private static final String SESSION_PREFIX = "nkc:auth:session:";
  private static final String ACCOUNT_SESSIONS_PREFIX = "nkc:auth:account-sessions:";

  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final Clock clock;

  @Override
  public void create(String sessionId, SessionSnapshot snapshot, Duration ttl) {
    String hash = hash(sessionId);
    String accountSessions = accountSessionsKey(snapshot.accountId());
    String value = json.writeValueAsString(snapshot);
    redisCall(
        () -> {
          redis.opsForValue().set(SESSION_PREFIX + hash, value, ttl);
          redis.opsForSet().add(accountSessions, hash);
          // The newest session has the latest absolute expiry, so the index lives until then.
          redis.expire(
              accountSessions, Duration.between(clock.instant(), snapshot.absoluteExpiresAt()));
          return null;
        });
  }

  @Override
  public Optional<SessionSnapshot> find(String sessionId) {
    String key = SESSION_PREFIX + hash(sessionId);
    String value = redisCall(() -> redis.opsForValue().get(key));
    if (value == null) return Optional.empty();
    try {
      return Optional.of(json.readValue(value, SessionSnapshot.class));
    } catch (JacksonException unreadable) {
      // A snapshot written by an incompatible version cannot be trusted; end it.
      redisCall(() -> redis.delete(key));
      return Optional.empty();
    }
  }

  @Override
  public Boolean touch(String sessionId, Duration ttl) {
    String key = SESSION_PREFIX + hash(sessionId);
    return Boolean.TRUE.equals(redisCall(() -> redis.expire(key, ttl)));
  }

  @Override
  public void delete(String sessionId) {
    String hash = hash(sessionId);
    Optional<SessionSnapshot> snapshot = find(sessionId);
    redisCall(() -> redis.delete(SESSION_PREFIX + hash));
    snapshot.ifPresent(
        session ->
            redisCall(
                () -> redis.opsForSet().remove(accountSessionsKey(session.accountId()), hash)));
  }

  @Override
  public void revokeAll(UUID accountId) {
    String accountSessions = accountSessionsKey(accountId);
    redisCall(
        () -> {
          Set<String> hashes = redis.opsForSet().members(accountSessions);
          if (hashes != null) hashes.forEach(hash -> redis.delete(SESSION_PREFIX + hash));
          redis.delete(accountSessions);
          return null;
        });
  }

  private static String accountSessionsKey(UUID accountId) {
    return ACCOUNT_SESSIONS_PREFIX + accountId;
  }

  private static String hash(String sessionId) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static <T> T redisCall(Supplier<T> call) {
    try {
      return call.get();
    } catch (DataAccessException failure) {
      throw new DependencyUnavailableException("Session store is unavailable", failure);
    }
  }
}
