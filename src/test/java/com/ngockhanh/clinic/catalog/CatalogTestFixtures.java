package com.ngockhanh.clinic.catalog;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import java.util.List;
import java.util.UUID;

public final class CatalogTestFixtures {
  private CatalogTestFixtures() {}

  public static UserPrincipal manager(String... permissions) {
    return UserPrincipal.builder()
        .userId(UUID.randomUUID())
        .staffId(UUID.randomUUID())
        .username("clinic_manager")
        .principalType("STAFF")
        .roleAssignments(
            List.of(
                new UserPrincipal.Assignment(
                    UUID.randomUUID(), "CLINIC_MANAGER", List.of(permissions))))
        .build();
  }
}
