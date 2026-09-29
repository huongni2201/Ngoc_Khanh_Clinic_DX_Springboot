package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.application.query.access.StaffPrincipal;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
final class StaffSessionSupport {
  private StaffSessionSupport() {
  }

  static String singleCookie(List<String> sessionIds, boolean authenticate) {
    if (sessionIds.size() > 1) {
      throw authenticate ? AuthenticationFailure.invalid() : AuthenticationFailure.invalidCookie();
    }
    return sessionIds.isEmpty() ? null : sessionIds.getFirst();
  }

  static StaffPrincipal principal(SessionTokens.Claims claims, Instant idleDeadline, Instant now) {
    var assignments = claims.roles().stream().filter(role -> role.effectiveAt(now))
        .map(role -> new StaffPrincipal.Assignment(role.assignmentId(), role.roleCode(), role.permissions(),
            role.departmentId(), role.roomId(), role.validFrom(), role.validTo()))
        .toList();
    if (assignments.isEmpty()) {
      throw AuthenticationFailure.invalid();
    }
    return new StaffPrincipal(claims.userId(), claims.staffId(), claims.username(), "STAFF",
        assignments, idleDeadline, claims.expiresAt());
  }

  static <T> T sessionDependency(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DependencyUnavailableException unavailable) {
      throw AuthenticationFailure.unavailable(unavailable);
    }
  }

  static void sessionDependency(Runnable operation) {
    sessionDependency(() -> {
      operation.run();
      return null;
    });
  }

  static void recordRevocation(AccountTransactions accounts, java.time.Clock clock, java.util.UUID userId,
                               boolean all, java.util.UUID correlationId) {
    try {
      accounts.revoked(userId, all, clock.instant(), correlationId);
      log.info("Staff session revocation recorded userId={} all={} correlationId={}",
          userId, all, correlationId);
    } catch (RuntimeException auditFailure) {
      log.error("Session revocation succeeded but audit failed userId={} correlationId={} failureType={}",
          userId, correlationId, auditFailure.getClass().getSimpleName());
    }
  }
}
