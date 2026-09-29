package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.domain.valueobject.StaffSessionPolicy;
import com.ngockhanh.clinic.identity.infrastructure.security.JwtSettings;
import com.ngockhanh.clinic.identity.infrastructure.session.LoginThrottleSettings;

import com.ngockhanh.clinic.identity.application.usecase.*;
import com.ngockhanh.clinic.identity.application.port.*;
import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import com.ngockhanh.clinic.identity.infrastructure.security.ServerJwtTokens;
import com.ngockhanh.clinic.identity.infrastructure.session.RedisSessionStore;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class SessionAdaptersTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    static StaffSessionPolicy settings() {
        return new StaffSessionPolicy(Duration.ofMinutes(30), Duration.ofHours(8));
    }

    static JwtSettings jwtSettings(String key) {
        return new JwtSettings("nkc", "nkc-staff", key);
    }

    static LoginThrottleSettings throttleSettings() {
        return new LoginThrottleSettings(10, 60, Duration.ofMinutes(15));
    }

    @Test
    void jwtKeepsScopedRolesAndRejectsTamperingAndExpiry() {
        Instant now = Instant.parse("2026-09-28T00:00:00Z");
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        var codec = new ServerJwtTokens(jwtSettings(key), settings(), Clock.fixed(now, ZoneOffset.UTC));
        UUID user = UUID.randomUUID();
        var role = new RoleAssignment(UUID.randomUUID(), "DOCTOR", List.of("READ"), UUID.randomUUID(), null, now, null);
        var claims = new SessionTokens.Claims(user, UUID.randomUUID(), "staff", UUID.randomUUID(), now, now.plusSeconds(60), List.of(role));
        String jwt = codec.issue(claims);
        assertThat(codec.verify(jwt)).contains(claims);
        assertThat(codec.verify(jwt.substring(0, jwt.lastIndexOf('.') + 1) + "invalid")).isEmpty();
        var later = new ServerJwtTokens(jwtSettings(key), settings(), Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC));
        assertThat(later.verify(jwt)).isEmpty();
    }

    @Test
    void redisRevocationPreventsStaleIssuanceAndTouchNeverResurrectsSession() {
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            var store = new RedisSessionStore(new StringRedisTemplate(factory));
            UUID user = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            long generation = store.generation(user);
            var session = new SessionStore.Stored(user, "internal-jwt", generation, now.plusSeconds(60));
            String id = "A".repeat(43);
            assertThat(store.create(id, session, Duration.ofSeconds(30), now)).isTrue();
            assertThat(store.find(id)).isEqualTo(session);
            assertThat(store.touch(id, session, Duration.ofSeconds(30), now.plusSeconds(45))).isEqualTo(session.absoluteExpiresAt());
            store.revokeAll(user);
            assertThat(store.find(id)).isNull();
            assertThat(store.touch(id, session, Duration.ofSeconds(30), now)).isNull();
            assertThat(store.create(id, session, Duration.ofSeconds(30), now)).isFalse();
        } finally {
            factory.destroy();
        }
    }

    @Test
    void redisIdleAndAbsoluteDeadlinesExpireWithoutResurrection() throws Exception {
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            var redis = new StringRedisTemplate(factory);
            var store = new RedisSessionStore(redis);
            UUID user = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            var session = new SessionStore.Stored(user, "jwt", store.generation(user), now.plusSeconds(60));
            String id = "B".repeat(43);
            assertThat(store.create(id, session, Duration.ofMillis(300), now)).isTrue();
            awaitSessionExpiry(store, id);
            assertThat(store.touch(id, session, Duration.ofMinutes(30), Instant.now())).isNull();

            now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            var absolute = new SessionStore.Stored(user, "jwt", session.generation(), now.plusMillis(400));
            assertThat(store.create(id, absolute, Duration.ofMinutes(30), now)).isTrue();
            assertThat(store.touch(id, absolute, Duration.ofMinutes(30), now)).isEqualTo(absolute.absoluteExpiresAt());
            awaitSessionExpiry(store, id);
            assertThat(store.touch(id, absolute, Duration.ofMinutes(30), Instant.now())).isNull();
        } finally {
            factory.destroy();
        }
    }

    @Test
    void concurrentIssuanceAndTouchCannotSurviveRevokeAll() throws Exception {
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var store = new RedisSessionStore(new StringRedisTemplate(factory));
            for (int attempt = 0; attempt < 10; attempt++) {
                UUID user = UUID.randomUUID();
                Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
                var session = new SessionStore.Stored(user, "jwt", store.generation(user), now.plusSeconds(60));
                String id = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        java.security.MessageDigest.getInstance("SHA-256").digest(user.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                var gate = new java.util.concurrent.CountDownLatch(1);
                var create = executor.submit(() -> {
                    gate.await();
                    return store.create(id, session, Duration.ofSeconds(30), now);
                });
                var revoke = executor.submit(() -> {
                    gate.await();
                    store.revokeAll(user);
                    return true;
                });
                gate.countDown();
                create.get();
                revoke.get();
                assertThat(store.find(id)).isNull();
                assertThat(store.touch(id, session, Duration.ofSeconds(30), now)).isNull();

                var fresh = new SessionStore.Stored(user, "jwt", store.generation(user), now.plusSeconds(60));
                assertThat(store.create(id, fresh, Duration.ofSeconds(30), now)).isTrue();
                var touchGate = new java.util.concurrent.CountDownLatch(1);
                var touch = executor.submit(() -> {
                    touchGate.await();
                    return store.touch(id, fresh, Duration.ofSeconds(30), now);
                });
                var secondRevoke = executor.submit(() -> {
                    touchGate.await();
                    store.revokeAll(user);
                    return true;
                });
                touchGate.countDown();
                touch.get();
                secondRevoke.get();
                assertThat(store.find(id)).isNull();
                assertThat(store.touch(id, fresh, Duration.ofSeconds(30), now)).isNull();
            }
        } finally {
            factory.destroy();
        }
    }

    @Test
    void throttleLimitsBothFailedUsernamesAndTotalIpAttempts() {
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            var throttle = new com.ngockhanh.clinic.identity.infrastructure.session.RedisLoginThrottle(
                    new StringRedisTemplate(factory), throttleSettings());
            String username = UUID.randomUUID().toString(), ip = UUID.randomUUID().toString();
            for (int i = 0; i < 10; i++) {
                throttle.check(username, ip);
                throttle.failed(username);
            }
            var userLimited = throttle.check(username, UUID.randomUUID().toString());
            assertThat(userLimited.allowed()).isFalse();
            assertThat(userLimited.retryAfterSeconds()).isBetween(1L, 900L);
            String otherIp = UUID.randomUUID().toString();
            for (int i = 0; i < 60; i++) throttle.check(UUID.randomUUID().toString(), otherIp);
            assertThat(throttle.check(UUID.randomUUID().toString(), otherIp).allowed()).isFalse();
        } finally {
            factory.destroy();
        }
    }

    @Test
    void lostRevocationMetadataCannotAuthorizeAnOldInflightLoginAfterReinitialization() {
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            var redis = new StringRedisTemplate(factory);
            var store = new RedisSessionStore(redis);
            UUID user = UUID.randomUUID();
            Instant now = Instant.now();
            var stale = new SessionStore.Stored(user, "old-jwt", store.generation(user), now.plusSeconds(60));
            redis.delete("nkc:auth:user:" + user + ":generation");
            assertThat(store.generation(user)).isNotEqualTo(stale.generation());
            assertThat(store.create("C".repeat(43), stale, Duration.ofSeconds(30), now)).isFalse();
        } finally {
            factory.destroy();
        }
    }

    private static void awaitSessionExpiry(RedisSessionStore store, String sessionId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);
        while (store.find(sessionId) != null && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertThat(store.find(sessionId)).isNull();
    }

    @Test
    void realRedisOutageRejectsLookupRevocationAndRateLimitChecks() {
        try (var isolated = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379)) {
            isolated.start();
            var server = new org.springframework.data.redis.connection.RedisStandaloneConfiguration(isolated.getHost(), isolated.getMappedPort(6379));
            var client = org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(300)).shutdownTimeout(Duration.ZERO).build();
            var factory = new LettuceConnectionFactory(server, client);
            factory.afterPropertiesSet();
            try {
                var redis = new StringRedisTemplate(factory);
                var store = new RedisSessionStore(redis);
                UUID user = UUID.randomUUID();
                store.generation(user);
                isolated.stop();
                assertThatThrownBy(() -> store.find("D".repeat(43))).isInstanceOf(DependencyUnavailableException.class);
                assertThatThrownBy(() -> store.revokeAll(user)).isInstanceOf(DependencyUnavailableException.class);
                var throttle = new com.ngockhanh.clinic.identity.infrastructure.session.RedisLoginThrottle(redis, throttleSettings());
                assertThatThrownBy(() -> throttle.check("user", "ip")).isInstanceOf(DependencyUnavailableException.class);
            } finally {
                factory.destroy();
            }
        }
    }
}
