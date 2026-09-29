package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;
import com.ngockhanh.clinic.identity.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.shared.audit.AuthAudit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountTransactions {
  private final StaffAccountRepository accounts;
  private final AuthAudit audit;

  @Transactional(readOnly = true)
  public UUID identify(String username) {
    log.debug("Looking up staff user by username");
    return accounts.identify(username);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public StaffAccount load(String username, Instant now) {
    return accounts.find(username, now);
  }

  @Transactional
  public void loginRecorded(UUID userId, Instant now, UUID correlation) {
    log.debug("Recording staff login audit userId={} correlationId={}", userId, correlation);
    if (accounts.recordLogin(userId, now, correlation) != 1) {
      throw AuthenticationFailure.invalid();
    }
    audit.record(userId, "STAFF_LOGIN", now, correlation);
  }

  @Transactional
  public void revoked(UUID userId, boolean all, Instant now, UUID correlation) {
    log.debug("Recording staff session revocation userId={} all={} correlationId={}", userId, all, correlation);
    audit.record(userId, all ? "STAFF_SESSIONS_REVOKED" : "STAFF_LOGOUT", now, correlation);
  }
}
