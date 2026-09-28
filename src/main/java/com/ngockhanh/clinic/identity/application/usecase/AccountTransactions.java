package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.domain.repository.StaffAccountRepository;

import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;

import com.ngockhanh.clinic.identity.application.port.*;
import com.ngockhanh.clinic.shared.audit.AuthAudit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AccountTransactions {
  private final StaffAccountRepository accounts;
  private final AuthAudit audit;

  public AccountTransactions(StaffAccountRepository accounts, AuthAudit audit) {
    this.accounts = accounts;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public UUID identify(String username) {
    return accounts.identify(username);
  }

  @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public StaffAccount load(String username, Instant now) {
    return accounts.find(username, now);
  }

  @Transactional
  public void loginRecorded(UUID userId, Instant now, UUID correlation) {
    accounts.recordLogin(userId, now, correlation);
    audit.record(userId, "STAFF_LOGIN", now, correlation);
  }

  @Transactional
  public void revoked(UUID userId, boolean all, Instant now, UUID correlation) {
    audit.record(userId, all ? "STAFF_SESSIONS_REVOKED" : "STAFF_LOGOUT", now, correlation);
  }
}
