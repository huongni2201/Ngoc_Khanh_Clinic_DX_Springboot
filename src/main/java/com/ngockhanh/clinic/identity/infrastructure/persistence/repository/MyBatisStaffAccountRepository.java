package com.ngockhanh.clinic.identity.infrastructure.persistence.repository;

import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;
import com.ngockhanh.clinic.identity.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.identity.infrastructure.persistence.converter.StaffAccountConverter;
import com.ngockhanh.clinic.identity.infrastructure.persistence.mapper.StaffLoginMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MyBatisStaffAccountRepository implements StaffAccountRepository {
  private final StaffLoginMapper mapper;

  @Override
  public UUID identify(String username) {
    return mapper.identify(username);
  }

  @Override
  public StaffAccount find(String username, Instant now) {
    var row = mapper.find(username);
    if (row == null) {
      return null;
    }
    return StaffAccountConverter.from(row, mapper.grants(row.userId(), now));
  }

  @Override
  public int recordLogin(UUID userId, Instant now, UUID correlationId) {
    return mapper.lastLogin(userId, now);
  }
}
