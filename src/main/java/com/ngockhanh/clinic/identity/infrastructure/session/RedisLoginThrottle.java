package com.ngockhanh.clinic.identity.infrastructure.session;

import com.ngockhanh.clinic.identity.application.usecase.*;
import com.ngockhanh.clinic.identity.application.port.*;
import com.ngockhanh.clinic.identity.application.port.LoginThrottle;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisLoginThrottle implements LoginThrottle {
  private final StringRedisTemplate redis;
  private final AuthSettings settings;
  private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
      local ipCount = redis.call('INCR', KEYS[1])
      if ipCount == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[3]) end
      local wait = 0
      if ipCount > tonumber(ARGV[2]) then wait = redis.call('PTTL', KEYS[1]) end
      if tonumber(redis.call('GET', KEYS[2]) or '0') >= tonumber(ARGV[1]) then
          wait = math.max(wait, redis.call('PTTL', KEYS[2]))
      end
      return wait
      """, Long.class);
  private static final DefaultRedisScript<Long> FAIL = new DefaultRedisScript<>("""
      local count = redis.call('INCR', KEYS[1])
      if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
      return count
      """, Long.class);

  public RedisLoginThrottle(StringRedisTemplate redis, AuthSettings settings) {
    this.redis = redis;
    this.settings = settings;
  }

  public void check(String username, String ip) {
    try {
      Long wait = redis.execute(CHECK, List.of("nkc:auth:limit:ip:" + RedisSessionStore.digest(ip), userKey(username)),
          Integer.toString(settings.usernameLimit()), Integer.toString(settings.ipLimit()),
          Long.toString(settings.throttleWindow().toMillis()));
      if (wait != null && wait > 0)
        throw new AuthenticationFailure(429, "Too many login attempts", Math.max(1, (wait + 999) / 1000));
    } catch (DataAccessException e) {
      throw AuthenticationFailure.unavailable();
    }
  }

  public void failed(String username) {
    try {
      redis.execute(FAIL, List.of(userKey(username)), Long.toString(settings.throttleWindow().toMillis()));
    } catch (DataAccessException e) {
      throw AuthenticationFailure.unavailable();
    }
  }

  private String userKey(String username) {
    return "nkc:auth:limit:user:" + RedisSessionStore.digest(username);
  }
}
