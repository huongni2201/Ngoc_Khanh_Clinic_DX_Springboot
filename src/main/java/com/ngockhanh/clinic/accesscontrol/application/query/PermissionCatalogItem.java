package com.ngockhanh.clinic.accesscontrol.application.query;

import java.util.List;
import lombok.Builder;

/**
 * Predefined permission that may be granted to a role (SRS 1.3.4). Permissions are seeded by
 * migrations and never created at runtime.
 *
 * @param module group shown on the Role Permissions screen
 * @param useCases related SRS use case IDs, such as {@code UC-008}; empty when none
 * @param sensitive whether the screen labels the permission "(sensitive)"
 */
@Builder
public record PermissionCatalogItem(
    String code, String name, String module, List<String> useCases, Boolean sensitive) {
  public PermissionCatalogItem {
    useCases = useCases == null ? List.of() : List.copyOf(useCases);
  }
}
