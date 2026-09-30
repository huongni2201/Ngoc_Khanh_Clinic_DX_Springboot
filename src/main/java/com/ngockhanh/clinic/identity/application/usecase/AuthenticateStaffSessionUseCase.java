package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.domain.valueobject.StaffSessionPolicy;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.application.query.AuthenticateStaffSessionQuery;
import com.ngockhanh.clinic.identity.application.query.StaffPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.function.Supplier;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticateStaffSessionUseCase {
    private final SessionStore sessions;
    private final SessionTokens tokens;
    private final StaffSessionPolicy sessionPolicy;
    private final Clock clock;

    public StaffPrincipal execute(AuthenticateStaffSessionQuery query) {
        String sessionId = singleSessionCookie(query.sessionIds());
        if (sessionId == null) {
            throw AuthenticationFailure.invalid();
        }
        SessionStore.Stored stored = sessionDependency(() -> sessions.find(sessionId));
        if (stored == null) {
            throw AuthenticationFailure.invalid();
        }

        SessionTokens.Claims claims = tokens.verify(stored.jwt()).orElse(null);
        if (claims == null) {
            rejectSession(sessionId);
        }
        var now = clock.instant();
        if (!claims.userId().equals(stored.userId()) || !claims.expiresAt().equals(stored.absoluteExpiresAt())
                || claims.roles().stream().noneMatch(role -> role.effectiveAt(now))) {
            rejectSession(sessionId);
        }

        var idleDeadline = sessionDependency(
                () -> sessions.touch(sessionId, stored, sessionPolicy.idleTimeout(), clock.instant()));
        if (idleDeadline == null) {
            throw AuthenticationFailure.invalid();
        }
        StaffPrincipal principal = StaffPrincipal.from(claims.userId(), claims.staffId(), claims.username(),
                claims.roles(), idleDeadline, claims.expiresAt(), clock.instant());
        log.debug("Authenticated staff session userId={}", principal.userId());
        return principal;
    }

    private void rejectSession(String sessionId) {
        sessionDependency(() -> sessions.delete(sessionId));
        throw AuthenticationFailure.invalid();
    }

    private String singleSessionCookie(List<String> sessionIds) {
        if (sessionIds.size() > 1) {
            throw AuthenticationFailure.invalid();
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
}
