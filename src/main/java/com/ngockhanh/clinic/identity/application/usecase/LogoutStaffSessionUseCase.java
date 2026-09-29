package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.command.LogoutStaffSessionCommand;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.shared.audit.AuthAudit;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
public class LogoutStaffSessionUseCase {
    private final SessionStore sessions;
    private final AuthAudit audit;
    private final Clock clock;
    private final TransactionOperations accountWriteTransaction;

    public LogoutStaffSessionUseCase(
            SessionStore sessions,
            AuthAudit audit,
            Clock clock,
            @Qualifier("staffAccountWriteTransaction") TransactionOperations accountWriteTransaction) {
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
        this.accountWriteTransaction = accountWriteTransaction;
    }

    public void execute(LogoutStaffSessionCommand command) {
        String sessionId = singleSessionCookie(command.sessionIds());
        var stored = sessionId == null ? null
                : sessionDependency(() -> sessions.find(sessionId));
        if (sessionId != null) {
            sessionDependency(() -> sessions.delete(sessionId));
        }
        if (stored != null) {
            recordRevocation(stored.userId(), false, command.correlationId());
            log.info("Staff session revoked userId={} correlationId={}", stored.userId(), command.correlationId());
        }
    }

    private String singleSessionCookie(List<String> sessionIds) {
        if (sessionIds.size() > 1) {
            throw AuthenticationFailure.invalidCookie();
        }
        return sessionIds.isEmpty() ? null : sessionIds.get(0);
    }

    private <T> T sessionDependency(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DependencyUnavailableException unavailable) {
            throw AuthenticationFailure.unavailable(unavailable);
        }
    }

    private void sessionDependency(Runnable operation) {
        try {
            operation.run();
        } catch (DependencyUnavailableException unavailable) {
            throw AuthenticationFailure.unavailable(unavailable);
        }
    }

    private void recordRevocation(UUID userId, boolean all, UUID correlationId) {
        try {
            accountWriteTransaction.executeWithoutResult(status ->
                    audit.record(userId, all ? "STAFF_SESSIONS_REVOKED" : "STAFF_LOGOUT", clock.instant(), correlationId));
            log.info("Staff session revocation recorded userId={} all={} correlationId={}",
                    userId, all, correlationId);
        } catch (RuntimeException auditFailure) {
            log.error("Session revocation succeeded but audit failed userId={} correlationId={} failureType={}",
                    userId, correlationId, auditFailure.getClass().getSimpleName());
        }
    }
}
