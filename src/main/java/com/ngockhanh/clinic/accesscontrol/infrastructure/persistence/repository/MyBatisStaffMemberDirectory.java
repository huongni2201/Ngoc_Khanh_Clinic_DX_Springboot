package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.application.port.out.StaffMemberDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.AssignableRole;
import com.ngockhanh.clinic.accesscontrol.application.query.StaffMemberSummary;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter.StaffMemberPersistenceConverter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.RoleMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.StaffMemberMyBatisMapper;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisStaffMemberDirectory implements StaffMemberDirectory {
  private final StaffMemberMyBatisMapper staffMembers;
  private final RoleMyBatisMapper roles;
  private final StaffMemberPersistenceConverter converter = new StaffMemberPersistenceConverter();

  @Override
  public PageResponse<StaffMemberSummary> search(
      Integer page, Integer size, String searchKey, String sortKey, String sortBy) {
    Long offset = (page.longValue() - 1) * size;
    String pattern = likePattern(searchKey);
    Long total = staffMembers.count(pattern);
    List<StaffMemberSummary> items =
        total == 0 || offset >= total
            ? List.of()
            : staffMembers.search(pattern, sortKey, sortBy, size, offset).stream()
                .map(converter::toSummary)
                .toList();
    return PageResponse.<StaffMemberSummary>builder()
        .items(items)
        .page(page)
        .size(size)
        .totalElements(total)
        .totalPages(Math.toIntExact((total + size - 1) / size))
        .build();
  }

  @Override
  public List<AssignableRole> findAssignableRoles() {
    return roles.findAssignable().stream().map(converter::toAssignableRole).toList();
  }

  /** Builds a contains pattern in which user-typed LIKE wildcards are matched literally. */
  private static String likePattern(String searchKey) {
    if (searchKey == null || searchKey.isBlank()) return null;
    String escaped = searchKey.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
