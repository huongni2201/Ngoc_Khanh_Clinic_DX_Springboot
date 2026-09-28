package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;

import com.ngockhanh.clinic.identity.application.query.access.*;
import com.ngockhanh.clinic.identity.application.port.access.*;
import com.ngockhanh.clinic.identity.application.port.*;
import com.ngockhanh.clinic.identity.application.command.StaffLoginCommand;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StaffAuthentication implements SessionRevocation {
  private static final Logger LOG = LoggerFactory.getLogger(StaffAuthentication.class);
  private final AccountTransactions accounts;
  private final Passwords passwords;
  private final SessionTokens tokens;
  private final SessionStore sessions;
  private final LoginThrottle throttle;
  private final AuthSettings settings;
  private final Clock clock;
  private final Supplier<String> sessionIds;

  public StaffAuthentication(AccountTransactions accounts, Passwords passwords, SessionTokens tokens,
                             SessionStore sessions, LoginThrottle throttle, AuthSettings settings,
                             Clock clock, Supplier<String> sessionIds) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.tokens = tokens;
    this.sessions = sessions;
    this.throttle = throttle;
    this.settings = settings;
    this.clock = clock;
    this.sessionIds = sessionIds;
  }

  public record Login(String sessionId, StaffPrincipal principal) {
    @Override
    public String toString() {
      return "Login[userId=" + principal.userId() + "]";
    }
  }

  public Login login(StaffLoginCommand command) {
    String username = command.username();
    String password = command.password();
    String ip = command.clientIp();
    String oldSessionId = command.previousSessionId();
    UUID correlation = command.correlationId();
    if (username == null || username.strip().isEmpty() || username.strip().length() > 200
        || password == null || password.isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new AuthenticationFailure(400, "Invalid login request");
    }
    username = username.strip();
    throttle.check(username, ip);
    UUID id = accounts.identify(username);
    long generation = id == null ? 0 : sessions.generation(id);
    Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    StaffAccount account = accounts.load(username, now);
    boolean passwordMatches = passwords.matches(password, account == null ? null : account.password());
    if (!passwordMatches || account == null || !account.eligible() || !account.userId().equals(id)) {
      throttle.failed(username);
      LOG.info("Staff login rejected correlationId={}", correlation);
      throw AuthenticationFailure.invalid();
    }
    var claims = new SessionTokens.Claims(id, account.staffId(), account.username(), UUID.randomUUID(),
        now, now.plus(settings.absoluteTimeout()), account.roles());
    String sessionId = sessionIds.get();
    var stored = new SessionStore.Stored(id, tokens.issue(claims), generation, claims.expiresAt());
    if (!sessions.create(sessionId, stored, settings.idleTimeout(), now)) throw AuthenticationFailure.invalid();
    try {
      accounts.loginRecorded(id, now, correlation);
      // Re-check after DB commit: logout-all may have raced with the audit transaction.
      Instant idleDeadline = sessions.touch(sessionId, stored, settings.idleTimeout(), clock.instant());
      if (idleDeadline == null) throw AuthenticationFailure.invalid();
      if (oldSessionId != null && !oldSessionId.equals(sessionId)) sessions.delete(oldSessionId);
      return new Login(sessionId, principal(claims, idleDeadline, clock.instant()));
    } catch (RuntimeException failure) {
      LOG.error("Staff login could not complete correlationId={} failureType={}", correlation, failure.getClass().getSimpleName());
      try {
        sessions.delete(sessionId);
      } catch (RuntimeException cleanup) {
        LOG.error("Unissued session cleanup failed correlationId={} failureType={}", correlation, cleanup.getClass().getSimpleName());
      }
      throw failure;
    }
  }

  public StaffPrincipal authenticate(String sessionId) {
    var stored = sessions.find(sessionId);
    if (stored == null) throw AuthenticationFailure.invalid();
    SessionTokens.Claims claims;
    try {
      claims = tokens.verify(stored.jwt());
      if (!claims.userId().equals(stored.userId()) || !claims.expiresAt().equals(stored.absoluteExpiresAt())) {
        throw AuthenticationFailure.invalid();
      }
      if (claims.roles().stream().noneMatch(role -> role.effectiveAt(clock.instant())))
        throw AuthenticationFailure.invalid();
    } catch (AuthenticationFailure invalid) {
      sessions.delete(sessionId);
      throw invalid;
    }
    Instant idleDeadline = sessions.touch(sessionId, stored, settings.idleTimeout(), clock.instant());
    if (idleDeadline == null) throw AuthenticationFailure.invalid();
    return principal(claims, idleDeadline, clock.instant());
  }

  public void logout(String sessionId, UUID correlation) {
    var stored = sessions.find(sessionId);
    sessions.delete(sessionId);
    if (stored != null) recordRevocation(stored.userId(), false, correlation);
  }

  public void logoutAll(UUID userId, UUID correlation) {
    sessions.revokeAll(userId);
    recordRevocation(userId, true, correlation);
  }

  public void revokeAllSessions(UUID userId) {
    logoutAll(userId, UUID.randomUUID());
  }

  private void recordRevocation(UUID userId, boolean all, UUID correlation) {
    try {
      accounts.revoked(userId, all, clock.instant(), correlation);
    } catch (RuntimeException auditFailure) {
      // Revocation already succeeded; do not restore access or report that the session is still active.
      LOG.error("Session revocation succeeded but audit failed userId={} correlationId={} failureType={}",
          userId, correlation, auditFailure.getClass().getSimpleName());
    }
  }

  private StaffPrincipal principal(SessionTokens.Claims claims, Instant idleDeadline, Instant now) {
    var roles = claims.roles().stream().filter(r -> r.effectiveAt(now))
        .map(r -> new StaffPrincipal.Assignment(r.assignmentId(), r.roleCode(), r.permissions(),
            r.departmentId(), r.roomId(), r.validFrom(), r.validTo())).toList();
    if (roles.isEmpty()) throw AuthenticationFailure.invalid();
    return new StaffPrincipal(claims.userId(), claims.staffId(), claims.username(), "STAFF",
        roles, idleDeadline, claims.expiresAt());
  }
}
