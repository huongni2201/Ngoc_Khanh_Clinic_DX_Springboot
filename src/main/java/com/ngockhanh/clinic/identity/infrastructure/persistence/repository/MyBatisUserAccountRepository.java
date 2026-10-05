package com.ngockhanh.clinic.identity.infrastructure.persistence.repository;

import com.ngockhanh.clinic.identity.domain.entity.UserAccount;
import com.ngockhanh.clinic.identity.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.identity.infrastructure.persistence.converter.UserAccountPersistenceConverter;
import com.ngockhanh.clinic.identity.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    return UserAccountPersistenceConverter.from(row, mapper.grants(row.accountId()));
  }

  @Override
  public boolean lockEligibleAccount(UUID accountId) {
    return mapper.lockEligibleAccount(accountId) != null;
  }
}
