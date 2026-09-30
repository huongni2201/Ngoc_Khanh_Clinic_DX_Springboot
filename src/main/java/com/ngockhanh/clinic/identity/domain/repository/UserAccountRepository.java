package com.ngockhanh.clinic.identity.domain.repository;

import com.ngockhanh.clinic.identity.domain.entity.UserAccount;

import java.time.Instant;
import java.util.UUID;

public interface UserAccountRepository {
    UUID identify(String username);

    UserAccount find(String username, Instant now);

    int recordLogin(UUID userId, Instant now);
}
