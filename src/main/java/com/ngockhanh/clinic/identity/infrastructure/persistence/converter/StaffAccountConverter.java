package com.ngockhanh.clinic.identity.infrastructure.persistence.converter;

import com.ngockhanh.clinic.identity.domain.entity.StaffAccount;
import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import com.ngockhanh.clinic.identity.infrastructure.persistence.projection.RoleGrantRow;
import com.ngockhanh.clinic.identity.infrastructure.persistence.projection.StaffLoginRow;

import java.util.*;
import java.util.stream.Collectors;

public final class StaffAccountConverter {
  private StaffAccountConverter() {
  }

  public static StaffAccount from(StaffLoginRow row, List<RoleGrantRow> grants) {
    var groups = grants.stream().collect(Collectors.groupingBy(
        RoleGrantRow::assignmentId, LinkedHashMap::new, Collectors.toList()));
    var roles = new ArrayList<RoleAssignment>();
    groups.values().forEach(rows -> {
      var first = rows.getFirst();
      roles.add(new RoleAssignment(first.assignmentId(), first.roleCode(),
          rows.stream().map(RoleGrantRow::permissionCode).filter(Objects::nonNull).toList(),
          first.departmentId(), first.roomId(), first.validFrom(), first.validTo()));
    });
    return new StaffAccount(row.userId(), row.staffId(), row.username(), row.password(),
        row.status(), row.principalType(), row.staffActive(), roles);
  }
}
