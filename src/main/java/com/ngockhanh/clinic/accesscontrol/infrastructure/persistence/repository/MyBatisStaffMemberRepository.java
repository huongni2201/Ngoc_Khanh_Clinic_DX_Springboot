package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.enums.StaffMemberStatus;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AccountOwner;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.StaffMemberMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.StaffAccountOwnerView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisStaffMemberRepository implements StaffMemberRepository {
  private final StaffMemberMyBatisMapper mapper;

  @Override
  public Optional<AccountOwner> lockForAccountLink(UUID staffMemberId) {
    StaffAccountOwnerView row = mapper.lockById(staffMemberId);
    if (row == null) return Optional.empty();
    return Optional.of(
        new AccountOwner(
            row.id(),
            AccountType.STAFF,
            StaffMemberStatus.ACTIVE.name().equals(row.status()),
            row.phone()));
  }

  @Override
  public List<StaffOwnerCandidate> searchActiveWithoutAccount(String keyword, int limit) {
    return mapper.searchActiveWithoutAccount(likePattern(keyword), limit).stream()
        .map(
            row ->
                new StaffOwnerCandidate(
                    row.id(),
                    row.staffCode(),
                    row.fullName(),
                    row.departmentName(),
                    row.phone(),
                    row.email()))
        .toList();
  }

  /** Builds a contains pattern in which user-typed LIKE wildcards are matched literally. */
  private static String likePattern(String keyword) {
    String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
