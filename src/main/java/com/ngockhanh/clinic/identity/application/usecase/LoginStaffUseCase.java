package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.command.LoginStaffCommand;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.LoginThrottle;
import com.ngockhanh.clinic.identity.application.port.Passwords;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.application.query.StaffPrincipal;
import com.ngockhanh.clinic.identity.application.response.StaffLoginResult;
import com.ngockhanh.clinic.identity.application.response.StaffSessionResponse;
import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;
import com.ngockhanh.clinic.identity.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.identity.domain.valueobject.StaffSessionPolicy;
import com.ngockhanh.clinic.shared.audit.AuthAudit;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
public class LoginStaffUseCase {
    private final StaffAccountRepository accounts;
    private final AuthAudit audit;
    private final Passwords passwords;
    private final SessionTokens tokens;
    private final SessionStore sessions;
    private final LoginThrottle throttle;
    private final StaffSessionPolicy sessionPolicy;
    private final Clock clock;
    private final Supplier<String> sessionIds;
    private final TransactionOperations accountReadTransaction;
    private final TransactionOperations accountSnapshotTransaction;
    private final TransactionOperations accountWriteTransaction;

    public LoginStaffUseCase(
            StaffAccountRepository accounts,
            AuthAudit audit,
            Passwords passwords,
            SessionTokens tokens,
            SessionStore sessions,
            LoginThrottle throttle,
            StaffSessionPolicy sessionPolicy,
            Clock clock,
            Supplier<String> sessionIds,
            @Qualifier("staffAccountReadTransaction") TransactionOperations accountReadTransaction,
            @Qualifier("staffAccountSnapshotTransaction") TransactionOperations accountSnapshotTransaction,
            @Qualifier("staffAccountWriteTransaction") TransactionOperations accountWriteTransaction) {
        this.accounts = accounts;
        this.audit = audit;
        this.passwords = passwords;
        this.tokens = tokens;
        this.sessions = sessions;
        this.throttle = throttle;
        this.sessionPolicy = sessionPolicy;
        this.clock = clock;
        this.sessionIds = sessionIds;
        this.accountReadTransaction = accountReadTransaction;
        this.accountSnapshotTransaction = accountSnapshotTransaction;
        this.accountWriteTransaction = accountWriteTransaction;
    }

    public StaffLoginResult execute(LoginStaffCommand command) {
        String username = command.username();
        String password = command.password();
        if (username == null || username.strip().isEmpty() || username.strip().length() > 200
                || password == null || password.isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw AuthenticationFailure.invalidRequest();
        }
        String normalizedUsername = username.strip();
        String previousSessionId = singleSessionCookie(command.sessionIds());

        LoginThrottle.CheckResult check = checkThrottle(normalizedUsername, command.clientIp());
        if (!check.allowed()) {
            throw AuthenticationFailure.rateLimited(check.retryAfterSeconds());
        }

        UUID userId = accountReadTransaction.execute(status -> accounts.identify(normalizedUsername));
        long generation = userId == null ? 0 : sessionDependency(() -> sessions.generation(userId));
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        StaffAccount account = accountSnapshotTransaction.execute(status -> accounts.find(normalizedUsername, now));
        boolean passwordMatches = passwords.matches(password, account == null ? null : account.password());
        if (!passwordMatches || account == null || !account.eligible() || !account.userId().equals(userId)) {
            failedAttempt(normalizedUsername);
            log.info("Staff login rejected correlationId={}", command.correlationId());
            throw AuthenticationFailure.invalid();
        }

        var claims = new SessionTokens.Claims(userId, account.staffId(), account.username(), UUID.randomUUID(),
                now, now.plus(sessionPolicy.absoluteTimeout()), account.roles());
        String sessionId = sessionIds.get();
        var stored = new SessionStore.Stored(userId, tokens.issue(claims), generation, claims.expiresAt());
        if (!sessionDependency(() -> sessions.create(sessionId, stored, sessionPolicy.idleTimeout(), now))) {
            throw AuthenticationFailure.invalid();
        }

        try {
            recordLogin(userId, now, command.correlationId());
            Instant idleDeadline = sessionDependency(
                    () -> sessions.touch(sessionId, stored, sessionPolicy.idleTimeout(), clock.instant()));
            if (idleDeadline == null) {
                throw AuthenticationFailure.invalid();
            }
            if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
                sessionDependency(() -> sessions.delete(previousSessionId));
            }
            StaffSessionResponse response = StaffSessionResponse.from(
                    StaffPrincipal.from(claims.userId(), claims.staffId(), claims.username(), claims.roles(),
                            idleDeadline, claims.expiresAt(), clock.instant()));
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

    private void recordLogin(UUID userId, Instant now, UUID correlationId) {
        log.debug("Recording staff login audit userId={} correlationId={}", userId, correlationId);
        accountWriteTransaction.executeWithoutResult(status -> {
            if (accounts.recordLogin(userId, now) != 1) {
                throw AuthenticationFailure.invalid();
            }
            audit.record(userId, "STAFF_LOGIN", now, correlationId);
        });
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
