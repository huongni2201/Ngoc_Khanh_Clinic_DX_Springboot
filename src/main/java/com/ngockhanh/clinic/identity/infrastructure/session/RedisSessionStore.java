package com.ngockhanh.clinic.identity.infrastructure.session;

import com.ngockhanh.clinic.identity.application.port.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

@Repository
public class RedisSessionStore implements SessionStore {
  private static final String PREFIX = "nkc:auth:";
  private final StringRedisTemplate redis;
  private final java.security.SecureRandom epochs = new java.security.SecureRandom();

  public RedisSessionStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  private static final DefaultRedisScript<Long> CREATE = script("""
      if redis.call('GET', KEYS[2]) ~= ARGV[3] then return 0 end
      if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
      if tonumber(ARGV[5]) <= tonumber(ARGV[6]) then return 0 end
      redis.call('HSET', KEYS[1], 'uid', ARGV[1], 'jwt', ARGV[2], 'gen', ARGV[3], 'exp', ARGV[4])
      redis.call('PEXPIREAT', KEYS[1], ARGV[5])
      redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', ARGV[6])
      redis.call('ZADD', KEYS[3], ARGV[4], KEYS[1])
      local last = redis.call('ZRANGE', KEYS[3], -1, -1, 'WITHSCORES')
      redis.call('PEXPIREAT', KEYS[3], last[2])
      return 1
      """);
  private static final DefaultRedisScript<Long> TOUCH = script("""
      if redis.call('HGET', KEYS[1], 'uid') ~= ARGV[1] or
         redis.call('HGET', KEYS[1], 'gen') ~= ARGV[2] or
         redis.call('GET', KEYS[2]) ~= ARGV[2] then return 0 end
      local deadline = math.min(tonumber(ARGV[3]), tonumber(redis.call('HGET', KEYS[1], 'exp')))
      if deadline <= tonumber(ARGV[4]) then return 0 end
      redis.call('PEXPIREAT', KEYS[1], deadline)
      return deadline
      """);
  private static final DefaultRedisScript<Long> DELETE = script("""
      local uid = redis.call('HGET', KEYS[1], 'uid')
      if uid then redis.call('ZREM', ARGV[1] .. 'user:' .. uid .. ':sessions', KEYS[1]) end
      return redis.call('DEL', KEYS[1])
      """);
  private static final DefaultRedisScript<Long> REVOKE = script("""
      redis.call('SETNX', KEYS[1], ARGV[1])
      redis.call('INCR', KEYS[1])
      local sessions = redis.call('ZRANGE', KEYS[2], 0, -1)
      for _, key in ipairs(sessions) do redis.call('DEL', key) end
      redis.call('DEL', KEYS[2])
      return 1
      """);

  public long generation(UUID userId) {
    return available(() -> {
      // A fresh random epoch prevents an old in-flight login matching a counter reset after Redis data loss.
      redis.opsForValue().setIfAbsent(generationKey(userId), newEpoch());
      return Long.parseLong(Objects.requireNonNull(redis.opsForValue().get(generationKey(userId))));
    });
  }

  public boolean create(String id, Stored value, Duration idle, Instant now) {
    return available(() -> {
      long deadline = Math.min(now.plus(idle).toEpochMilli(), value.absoluteExpiresAt().toEpochMilli());
      return Long.valueOf(1).equals(redis.execute(CREATE,
          List.of(key(id), generationKey(value.userId()), indexKey(value.userId())),
          value.userId().toString(), value.jwt(), Long.toString(value.generation()),
          Long.toString(value.absoluteExpiresAt().toEpochMilli()), Long.toString(deadline), Long.toString(now.toEpochMilli())));
    });
  }

  public Stored find(String id) {
    if (!validId(id)) return null;
    return available(() -> {
      var data = redis.opsForHash().entries(key(id));
      if (data.isEmpty()) return null;
      try {
        return new Stored(UUID.fromString((String) data.get("uid")), (String) data.get("jwt"),
            Long.parseLong((String) data.get("gen")), Instant.ofEpochMilli(Long.parseLong((String) data.get("exp"))));
      } catch (RuntimeException e) {
        throw AuthenticationFailure.invalid();
      }
    });
  }

  public Instant touch(String id, Stored expected, Duration idle, Instant now) {
    return available(() -> {
      Long result = redis.execute(TOUCH, List.of(key(id), generationKey(expected.userId())),
          expected.userId().toString(), Long.toString(expected.generation()),
          Long.toString(now.plus(idle).toEpochMilli()), Long.toString(now.toEpochMilli()));
      return result == null || result == 0 ? null : Instant.ofEpochMilli(result);
    });
  }

  public void delete(String id) {
    if (validId(id)) available(() -> redis.execute(DELETE, List.of(key(id)), PREFIX));
  }

  public void revokeAll(UUID userId) {
    available(() -> redis.execute(REVOKE, List.of(generationKey(userId), indexKey(userId)), newEpoch()));
  }

  private String newEpoch() {
    return Long.toString(epochs.nextLong(1L << 62));
  }

  private static String generationKey(UUID uid) {
    return PREFIX + "user:" + uid + ":generation";
  }

  private static String indexKey(UUID uid) {
    return PREFIX + "user:" + uid + ":sessions";
  }

  public static boolean validId(String id) {
    return id != null && id.matches("[A-Za-z0-9_-]{43}");
  }

  public static String digest(String input) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String key(String id) {
    return PREFIX + "session:" + digest(id);
  }

  private static DefaultRedisScript<Long> script(String text) {
    return new DefaultRedisScript<>(text, Long.class);
  }

  private static <T> T available(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DataAccessException e) {
      throw AuthenticationFailure.unavailable();
    }
  }
}
