package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.command.LoginCommand;
import com.ngockhanh.clinic.accesscontrol.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionSnapshot;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionStore;
import com.ngockhanh.clinic.accesscontrol.application.response.LoginResult;
import com.ngockhanh.clinic.accesscontrol.domain.entity.UserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SessionPolicy;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Signs a user in with username and password and issues a server-side session (UC-001, ADR-0014).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCase {
  static final String ACCOUNT_LOGIN = "ACCOUNT_LOGIN";
  static final String ACCOUNT_LOGIN_FAILED = "ACCOUNT_LOGIN_FAILED";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserAccountRepository accounts;
  private final PasswordEncoder passwords;
  private final SessionStore sessions;
  private final AuditWriter audit;
  private final TransactionOperations transactions;
  private final SessionPolicy policy;
  private final Clock clock;

  /**
   * Validates the credentials and account status, ends the browser's previous session and creates a
   * new one.
   *
   * <p>Every rejection returns the same failure so callers cannot tell whether the username exists.
   * A rejection for an existing account is audited in its own committed transaction. The successful
   * sign-in audit commits before the session is stored; if storing the session fails, no session ID
   * is returned.
   *
   * @param command username and password exactly as submitted, previous session ID and correlation
   * @return the new session ID (for the cookie only) and the authenticated principal
   * @throws AuthenticationFailure if the credentials are invalid or the account cannot sign in
   * @throws com.ngockhanh.clinic.shared.exception.DependencyUnavailableException if the session
   *     store is unavailable
   */
  public LoginResult execute(LoginCommand command) {
    if (command.previousSessionId() != null) sessions.delete(command.previousSessionId());

    UserAccount account = accounts.findByUsername(command.username()).orElse(null);
    if (account == null) {
      log.warn("Sign-in rejected for unknown username: correlationId={}", command.correlationId());
      throw AuthenticationFailure.invalidCredentials();
    }
    if (!passwords.matches(command.password(), account.passwordHash()) || !account.eligible()) {
      Instant failedAt = clock.instant();
      transactions.executeWithoutResult(
          status ->
              audit.record(account.id(), ACCOUNT_LOGIN_FAILED, failedAt, command.correlationId()));
      log.warn(
          "Sign-in rejected: accountId={}, correlationId={}",
          account.id(),
          command.correlationId());
      throw AuthenticationFailure.invalidCredentials();
    }

    Instant now = clock.instant();
    SessionSnapshot snapshot = snapshot(account, now);
    String sessionId = newSessionId();
    // Audit commits first; storing the session is the last step so a failure leaves no session.
    transactions.executeWithoutResult(
        status -> audit.record(account.id(), ACCOUNT_LOGIN, now, command.correlationId()));
    sessions.create(sessionId, snapshot, policy.idleTimeout());
    log.info("Sign-in session created: accountId={}", account.id());
    return LoginResult.builder()
        .sessionId(sessionId)
        .principal(snapshot.toPrincipal(now.plus(policy.idleTimeout())))
        .build();
  }

  private SessionSnapshot snapshot(UserAccount account, Instant now) {
    return SessionSnapshot.builder()
        .accountId(account.id())
        .accountType(account.accountType().name())
        .staffMemberId(account.staffMemberId())
        .patientId(account.patientId())
        .username(account.username())
        .roles(
            account.roles().stream()
                .map(
                    grant ->
                        SessionSnapshot.Role.builder()
                            .roleId(grant.roleId())
                            .roleCode(grant.roleCode())
                            .permissions(grant.permissions())
                            .build())
                .toList())
        .createdAt(now)
        .absoluteExpiresAt(now.plus(policy.absoluteTimeout()))
        .build();
  }

  private static String newSessionId() {
    byte[] bytes = new byte[32]; // binary buffer for the random session ID
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
