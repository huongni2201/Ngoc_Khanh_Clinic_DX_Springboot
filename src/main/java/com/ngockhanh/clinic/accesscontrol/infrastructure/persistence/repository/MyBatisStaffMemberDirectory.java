package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.application.port.out.StaffMemberDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.AssignableRole;
import com.ngockhanh.clinic.accesscontrol.application.query.StaffMemberSummary;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter.StaffMemberPersistenceConverter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.RoleMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.StaffMemberMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.AccountRoleCodeView;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.StaffMemberListView;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    List<StaffMemberListView> rows =
        total == 0 || offset >= total
            ? List.of()
            : staffMembers.search(pattern, sortKey, sortBy, size, offset);
    Map<UUID, List<String>> roleCodes = roleCodesByAccount(rows);
    List<StaffMemberSummary> items =
        rows.stream()
            .map(
                row ->
                    converter.toSummary(
                        row, roleCodes.getOrDefault(row.accountId(), List.of())))
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

  /** Loads the role codes of the whole page in one query instead of one per row. */
  private Map<UUID, List<String>> roleCodesByAccount(List<StaffMemberListView> rows) {
    List<UUID> accountIds =
        rows.stream().map(StaffMemberListView::accountId).filter(Objects::nonNull).toList();
    if (accountIds.isEmpty()) return Map.of();
    Map<UUID, List<String>> codes = new HashMap<>();
    for (AccountRoleCodeView grant : staffMembers.findActiveRoleCodes(accountIds))
      codes.computeIfAbsent(grant.accountId(), id -> new ArrayList<>()).add(grant.roleCode());
    return codes;
  }

  /** Builds a contains pattern in which user-typed LIKE wildcards are matched literally. */
  private static String likePattern(String searchKey) {
    if (searchKey == null || searchKey.isBlank()) return null;
    String escaped = searchKey.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
