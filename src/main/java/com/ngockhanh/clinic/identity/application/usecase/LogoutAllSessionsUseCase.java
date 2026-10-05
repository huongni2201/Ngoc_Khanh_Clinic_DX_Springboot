package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.identity.application.command.LogoutAllSessionsCommand;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.SessionRevocation;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import java.time.Clock;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/** Revokes all sessions for an account and records its audit event. */
@Service
@Slf4j
public class LogoutAllSessionsUseCase implements SessionRevocation {
  private final SessionStore sessions;
  private final AuditWriter audit;
  private final Clock clock;
  private final TransactionOperations accountWriteTransaction;

  public LogoutAllSessionsUseCase(
      SessionStore sessions,
      AuditWriter audit,
      Clock clock,
      @Qualifier("accountWriteTransaction") TransactionOperations accountWriteTransaction) {
    this.sessions = sessions;
    this.audit = audit;
    this.clock = clock;
    this.accountWriteTransaction = accountWriteTransaction;
  }

  /**
   * Revokes the account's sessions; a subsequent audit failure does not undo the revocation.
   *
   * @param command account identifier and request correlation context
   * @throws AuthenticationFailure if the session store is unavailable
   */
  public void execute(LogoutAllSessionsCommand command) {
    sessionDependency(() -> sessions.revokeAll(command.userId()));
    recordRevocation(command.userId(), command.correlationId());
    log.info(
        "All user sessions revoked userId={} correlationId={}",
        command.userId(),
        command.correlationId());
  }

  @Override
  public void revokeAllSessions(UUID userId) {
    execute(
        LogoutAllSessionsCommand.builder().userId(userId).correlationId(UUID.randomUUID()).build());
  }

  private void sessionDependency(Runnable operation) {
    try {
      operation.run();
    } catch (DependencyUnavailableException unavailable) {
      throw AuthenticationFailure.unavailable(unavailable);
    }
  }

  private void recordRevocation(UUID userId, UUID correlationId) {
    try {
      accountWriteTransaction.executeWithoutResult(
          status ->
              audit.record(userId, "ACCOUNT_SESSIONS_REVOKED", clock.instant(), correlationId));
      log.info(
          "User session revocation recorded userId={} all=true correlationId={}",
          userId,
          correlationId);
    } catch (RuntimeException auditFailure) {
      log.error(
          "Session revocation succeeded but audit failed userId={} correlationId={} failureType={}",
          userId,
          correlationId,
          auditFailure.getClass().getSimpleName());
    }
  }
}
