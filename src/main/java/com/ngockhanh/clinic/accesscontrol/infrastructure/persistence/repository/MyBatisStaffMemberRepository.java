package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffMember;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter.StaffMemberPersistenceConverter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.StaffMemberMyBatisMapper;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisStaffMemberRepository implements StaffMemberRepository {
  static final String STAFF_CODE_PREFIX = "NV-";
  private static final String STAFF_CODE_LOCK = "nkc:staff-code";

  private final StaffMemberMyBatisMapper mapper;
  private final StaffMemberPersistenceConverter converter = new StaffMemberPersistenceConverter();

  @Override
  public String nextStaffCode() {
    // Serializes code allocation until commit, so two creations never read the same maximum.
    mapper.lockSequence(STAFF_CODE_LOCK);
    return String.format(
        "%s%04d", STAFF_CODE_PREFIX, mapper.highestStaffCodeNumber(STAFF_CODE_PREFIX) + 1);
  }

  @Override
  public void save(StaffMember staffMember) {
    if (!Objects.equals(mapper.insert(converter.toRecord(staffMember)), 1))
      throw new IllegalStateException("Staff member was not inserted");
  }
}
