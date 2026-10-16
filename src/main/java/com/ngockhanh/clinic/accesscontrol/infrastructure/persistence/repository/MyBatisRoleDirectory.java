package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.application.port.out.RoleDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.PermissionCatalogItem;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter.RolePersistenceConverter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.RoleMyBatisMapper;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisRoleDirectory implements RoleDirectory {
  private final RoleMyBatisMapper roles;
  private final RolePersistenceConverter converter = new RolePersistenceConverter();

  @Override
  public PageResponse<RoleSummary> search(
      Integer page, Integer size, String searchKey, Boolean active, String sortKey, String sortBy) {
    Long offset = (page.longValue() - 1) * size;
    String pattern = likePattern(searchKey);
    Long total = roles.count(pattern, active);
    List<RoleSummary> items =
        total == 0 || offset >= total
            ? List.of()
            : roles.search(pattern, active, sortKey, sortBy, size, offset).stream()
                .map(converter::toSummary)
                .toList();
    return PageResponse.<RoleSummary>builder()
        .items(items)
        .page(page)
        .size(size)
        .totalElements(total)
        .totalPages(Math.toIntExact((total + size - 1) / size))
        .build();
  }

  @Override
  public Optional<RoleSummary> findSummary(UUID roleId) {
    if (roleId == null) return Optional.empty();
    return Optional.ofNullable(roles.findListViewById(roleId)).map(converter::toSummary);
  }

  @Override
  public List<PermissionCatalogItem> findPermissionCatalog() {
    return roles.findPermissionCatalog().stream().map(converter::toCatalogItem).toList();
  }

  /** Builds a contains pattern in which user-typed LIKE wildcards are matched literally. */
  private static String likePattern(String searchKey) {
    if (searchKey == null || searchKey.isBlank()) return null;
    String escaped = searchKey.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
