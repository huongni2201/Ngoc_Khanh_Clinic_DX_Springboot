package com.ngockhanh.clinic.identity.infrastructure.persistence.converter;

import com.ngockhanh.clinic.identity.domain.entity.UserAccount;
import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import com.ngockhanh.clinic.identity.infrastructure.persistence.projection.RoleGrantRow;
import com.ngockhanh.clinic.identity.infrastructure.persistence.projection.UserLoginRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UserAccountPersistenceConverter {
    private UserAccountPersistenceConverter() {
    }

    public static UserAccount from(UserLoginRow row, List<RoleGrantRow> grants) {
        var groups = grants.stream().collect(Collectors.groupingBy(
                RoleGrantRow::assignmentId, LinkedHashMap::new, Collectors.toList()));
        var roles = new ArrayList<RoleAssignment>();
        groups.values().forEach(rows -> {
            var first = rows.getFirst();
            roles.add(new RoleAssignment(first.assignmentId(), first.roleCode(),
                    rows.stream().map(RoleGrantRow::permissionCode).filter(Objects::nonNull).toList(),
                    first.departmentId(), first.roomId(), first.validFrom(), first.validTo()));
        });
        return new UserAccount(row.userId(), row.staffId(), row.patientId(), row.username(), row.password(),
                row.status(), row.principalType(), row.staffActive(), roles);
    }
}
