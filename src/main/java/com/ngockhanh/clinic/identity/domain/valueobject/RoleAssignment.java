package com.ngockhanh.clinic.identity.domain.valueobject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleAssignment(UUID assignmentId, String roleCode, List<String> permissions,
                             UUID departmentId, UUID roomId, Instant validFrom, Instant validTo) {
    public RoleAssignment {
        if (assignmentId == null || roleCode == null || roleCode.isBlank() || validFrom == null || permissions == null
                || permissions.stream().anyMatch(p -> p == null || p.isBlank())
                || (validTo != null && !validTo.isAfter(validFrom))) {
            throw new IllegalArgumentException("Invalid role assignment");
        }
        permissions = permissions.stream().distinct().sorted().toList();
    }

    public boolean effectiveAt(Instant now) {
        return !validFrom.isAfter(now) && (validTo == null || now.isBefore(validTo));
    }
}
