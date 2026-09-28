package com.ngockhanh.clinic.identity.domain.repository;

import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;

import java.time.Instant;
import java.util.UUID;

public interface StaffAccountRepository {
  UUID identify(String username);

  StaffAccount find(String username, Instant now);

  void recordLogin(UUID userId, Instant now, UUID correlationId);
}
