package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter;

import com.ngockhanh.clinic.accesscontrol.application.query.PermissionCatalogItem;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.RoleRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.RoleListView;
import java.util.Arrays;
import java.util.List;

/** Maps role administration domain objects, records and SQL views without querying. */
public final class RolePersistenceConverter {
  public RoleRecord toRecord(Role role) {
    return RoleRecord.builder()
        .id(role.id())
        .code(role.code())
        .name(role.name())
        .description(role.description())
        .active(role.active())
        .rowVersion(role.rowVersion())
        .build();
  }

  public Role toDomain(RoleRecord record) {
    return Role.restore(
        record.id(),
        record.code(),
        record.name(),
        record.description(),
        record.active(),
        record.rowVersion());
  }

  public RoleSummary toSummary(RoleListView row) {
    return RoleSummary.builder()
        .id(row.id())
        .code(row.code())
        .name(row.name())
        .description(row.description())
        .active(row.active())
        .permissionCount(row.permissionCount())
        .userCount(row.userCount())
        .rowVersion(row.rowVersion())
        .build();
  }

  /** {@code use_cases} holds IDs separated by commas, such as {@code UC-041, UC-042}. */
  public PermissionCatalogItem toCatalogItem(PermissionRecord record) {
    List<String> useCases =
        record.useCases() == null
            ? List.of()
            : Arrays.stream(record.useCases().split(","))
                .map(String::trim)
                .filter(useCase -> !useCase.isEmpty())
                .toList();
    return PermissionCatalogItem.builder()
        .code(record.code())
        .name(record.name())
        .module(record.module())
        .useCases(useCases)
        .sensitive(record.sensitive())
        .build();
  }
}
