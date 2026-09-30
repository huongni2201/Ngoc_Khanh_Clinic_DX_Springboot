package com.ngockhanh.clinic.identity.application.query;

import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffPrincipal(UUID userId, UUID staffId, String username, String principalType,
                             List<Assignment> roleAssignments, Instant idleExpiresAt, Instant absoluteExpiresAt) {
    public StaffPrincipal {
        roleAssignments = List.copyOf(roleAssignments);
    }

    public static StaffPrincipal from(UUID userId, UUID staffId, String username,
                                      List<RoleAssignment> assignments, Instant idleExpiresAt,
                                      Instant absoluteExpiresAt, Instant now) {
        List<Assignment> effectiveAssignments = assignments.stream()
                .filter(assignment -> assignment.effectiveAt(now))
                .map(assignment -> new Assignment(assignment.assignmentId(), assignment.roleCode(),
                        assignment.permissions(), assignment.departmentId(), assignment.roomId(),
                        assignment.validFrom(), assignment.validTo()))
                .toList();
        if (effectiveAssignments.isEmpty()) {
            throw AuthenticationFailure.invalid();
        }
        return new StaffPrincipal(userId, staffId, username, "STAFF", effectiveAssignments,
                idleExpiresAt, absoluteExpiresAt);
    }

    public record Assignment(UUID assignmentId, String roleCode, List<String> permissions,
                             UUID departmentId, UUID roomId, Instant validFrom, Instant validTo) {
        public Assignment {
            permissions = List.copyOf(permissions);
        }
    }
}
