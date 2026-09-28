package com.ngockhanh.clinic.identity.infrastructure.persistence.repository;

import com.ngockhanh.clinic.identity.domain.repository.StaffAccountRepository;

import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;

import com.ngockhanh.clinic.identity.application.port.*;
import com.ngockhanh.clinic.identity.infrastructure.persistence.converter.StaffAccountConverter;
import com.ngockhanh.clinic.identity.infrastructure.persistence.mapper.StaffLoginMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

@Repository
public class MyBatisStaffAccountRepository implements StaffAccountRepository {
  private final StaffLoginMapper mapper;

  public MyBatisStaffAccountRepository(StaffLoginMapper mapper) {
    this.mapper = mapper;
  }

  public UUID identify(String username) {
    return mapper.identify(username);
  }

  public StaffAccount find(String username, Instant now) {
    var row = mapper.find(username);
    if (row == null) return null;
    return StaffAccountConverter.from(row, mapper.grants(row.userId(), now));
  }

  public void recordLogin(UUID userId, Instant now, UUID correlationId) {
    if (mapper.lastLogin(userId, now) != 1)
      throw com.ngockhanh.clinic.identity.application.port.AuthenticationFailure.invalid();
  }
}
