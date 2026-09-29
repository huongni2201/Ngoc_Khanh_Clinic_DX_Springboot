package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.AuthSettings;
import com.ngockhanh.clinic.identity.application.command.StaffLoginCommand;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.LoginThrottle;
import com.ngockhanh.clinic.identity.application.port.Passwords;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.application.response.StaffLoginResult;
import com.ngockhanh.clinic.identity.application.response.StaffSessionResponse;
import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffLoginUseCase {
  private final AccountTransactions accounts;
  private final Passwords passwords;
  private final SessionTokens tokens;
  private final SessionStore sessions;
  private final LoginThrottle throttle;
  private final AuthSettings settings;
  private final Clock clock;
  private final Supplier<String> sessionIds;

  public StaffLoginResult execute(StaffLoginCommand command) {
    String username = command.username();
    String password = command.password();
    if (username == null || username.strip().isEmpty() || username.strip().length() > 200
        || password == null || password.isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw AuthenticationFailure.invalidRequest();
    }
    username = username.strip();
    String previousSessionId = StaffSessionSupport.singleCookie(command.sessionIds(), false);

    LoginThrottle.CheckResult check = checkThrottle(username, command.clientIp());
    if (!check.allowed()) {
      throw AuthenticationFailure.rateLimited(check.retryAfterSeconds());
    }

    UUID userId = accounts.identify(username);
    long generation = userId == null ? 0 : sessionDependency(() -> sessions.generation(userId));
    Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    StaffAccount account = accounts.load(username, now);
    boolean passwordMatches = passwords.matches(password, account == null ? null : account.password());
    if (!passwordMatches || account == null || !account.eligible() || !account.userId().equals(userId)) {
      failedAttempt(username);
      log.info("Staff login rejected correlationId={}", command.correlationId());
      throw AuthenticationFailure.invalid();
    }

    var claims = new SessionTokens.Claims(userId, account.staffId(), account.username(), UUID.randomUUID(),
        now, now.plus(settings.absoluteTimeout()), account.roles());
    String sessionId = sessionIds.get();
    var stored = new SessionStore.Stored(userId, tokens.issue(claims), generation, claims.expiresAt());
    if (!sessionDependency(() -> sessions.create(sessionId, stored, settings.idleTimeout(), now))) {
      throw AuthenticationFailure.invalid();
    }

    try {
      accounts.loginRecorded(userId, now, command.correlationId());
      Instant idleDeadline = sessionDependency(
          () -> sessions.touch(sessionId, stored, settings.idleTimeout(), clock.instant()));
      if (idleDeadline == null) {
        throw AuthenticationFailure.invalid();
      }
      if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
        sessionDependency(() -> sessions.delete(previousSessionId));
      }
      StaffSessionResponse response = StaffSessionResponse.from(
          StaffSessionSupport.principal(claims, idleDeadline, clock.instant()));
      log.info("Staff login completed userId={} correlationId={}", userId, command.correlationId());
      return new StaffLoginResult(sessionId, response);
    } catch (RuntimeException failure) {
      log.error("Staff login could not complete correlationId={} failureType={}",
          command.correlationId(), failure.getClass().getSimpleName());
      cleanupUnissuedSession(sessionId, command.correlationId());
      throw failure;
    }
  }

  private LoginThrottle.CheckResult checkThrottle(String username, String ip) {
    try {
      return throttle.check(username, ip);
    } catch (DependencyUnavailableException unavailable) {
      throw AuthenticationFailure.unavailable(unavailable);
    }
  }

  private void failedAttempt(String username) {
    try {
      throttle.failed(username);
    } catch (DependencyUnavailableException unavailable) {
      throw AuthenticationFailure.unavailable(unavailable);
    }
  }

  private <T> T sessionDependency(Supplier<T> operation) {
    return StaffSessionSupport.sessionDependency(operation);
  }

  private void sessionDependency(Runnable operation) {
    StaffSessionSupport.sessionDependency(operation);
  }

  private void cleanupUnissuedSession(String sessionId, UUID correlationId) {
    try {
      sessionDependency(() -> sessions.delete(sessionId));
    } catch (RuntimeException cleanupFailure) {
      log.error("Unissued session cleanup failed correlationId={} failureType={}",
          correlationId, cleanupFailure.getClass().getSimpleName());
    }
  }
}
