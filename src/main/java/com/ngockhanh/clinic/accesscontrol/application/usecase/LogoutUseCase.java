package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.command.LogoutCommand;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionSnapshot;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionStore;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/** Ends the current session (UC-002). */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase {
  static final String ACCOUNT_LOGOUT = "ACCOUNT_LOGOUT";

  private final SessionStore sessions;
  private final AuditWriter audit;
  private final TransactionOperations transactions;
  private final Clock clock;

  /**
   * Ends the session if it exists and audits the sign-out of a live session. Missing or expired
   * sessions are ignored. Other sessions of the same account stay active.
   *
   * @param command session ID from the cookie, possibly null, and correlation
   * @throws com.ngockhanh.clinic.shared.exception.DependencyUnavailableException if the session
   *     store is unavailable
   */
  public void execute(LogoutCommand command) {
    if (command.sessionId() == null || command.sessionId().isBlank()) return;
    Optional<SessionSnapshot> session = sessions.find(command.sessionId());
    sessions.delete(command.sessionId());
    session.ifPresent(
        snapshot -> {
          transactions.executeWithoutResult(
              status ->
                  audit.record(
                      snapshot.accountId(),
                      ACCOUNT_LOGOUT,
                      clock.instant(),
                      command.correlationId()));
          log.info("Session ended by sign-out: accountId={}", snapshot.accountId());
        });
  }
}
