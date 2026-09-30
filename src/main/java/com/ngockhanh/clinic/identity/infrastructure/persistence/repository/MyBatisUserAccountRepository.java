package com.ngockhanh.clinic.identity.infrastructure.persistence.repository;

import com.ngockhanh.clinic.identity.domain.entity.UserAccount;
import com.ngockhanh.clinic.identity.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.identity.infrastructure.persistence.converter.UserAccountPersistenceConverter;
import com.ngockhanh.clinic.identity.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MyBatisUserAccountRepository implements UserAccountRepository {
    private final UserLoginMyBatisMapper mapper;

    @Override
    public UUID identify(String username) {
        return mapper.identify(username);
    }

    @Override
    public UserAccount find(String username, Instant now) {
        var row = mapper.find(username);
        if (row == null) {
            return null;
        }
        return UserAccountPersistenceConverter.from(row, mapper.grants(row.userId(), now));
    }

    @Override
    public int recordLogin(UUID userId, Instant now) {
        return mapper.lastLogin(userId, now);
    }
}
