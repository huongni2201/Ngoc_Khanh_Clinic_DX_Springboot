package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.command.LogoutAllStaffSessionsCommand;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.SessionRevocation;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.shared.audit.AuthAudit;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.UUID;

@Service
@Slf4j
public class LogoutAllStaffSessionsUseCase implements SessionRevocation {
    private final SessionStore sessions;
    private final AuthAudit audit;
    private final Clock clock;
    private final TransactionOperations accountWriteTransaction;

    public LogoutAllStaffSessionsUseCase(
            SessionStore sessions,
            AuthAudit audit,
            Clock clock,
            @Qualifier("staffAccountWriteTransaction") TransactionOperations accountWriteTransaction) {
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
        this.accountWriteTransaction = accountWriteTransaction;
    }

    public void execute(LogoutAllStaffSessionsCommand command) {
        sessionDependency(() -> sessions.revokeAll(command.userId()));
        recordRevocation(command.userId(), command.correlationId());
        log.info("All staff sessions revoked userId={} correlationId={}",
                command.userId(), command.correlationId());
    }

    @Override
    public void revokeAllSessions(UUID userId) {
        execute(new LogoutAllStaffSessionsCommand(userId, UUID.randomUUID()));
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
            accountWriteTransaction.executeWithoutResult(status ->
                    audit.record(userId, "STAFF_SESSIONS_REVOKED", clock.instant(), correlationId));
            log.info("Staff session revocation recorded userId={} all=true correlationId={}", userId, correlationId);
        } catch (RuntimeException auditFailure) {
            log.error("Session revocation succeeded but audit failed userId={} correlationId={} failureType={}",
                    userId, correlationId, auditFailure.getClass().getSimpleName());
        }
    }
}
