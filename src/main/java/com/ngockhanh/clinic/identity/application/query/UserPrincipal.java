package com.ngockhanh.clinic.identity.application.query;

import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("access")
public record UserPrincipal(UUID userId, UUID staffId, UUID patientId, String username, String principalType,
                             List<Assignment> roleAssignments, Instant idleExpiresAt, Instant absoluteExpiresAt) {
    public UserPrincipal {
        roleAssignments = List.copyOf(roleAssignments);
    }

    public static UserPrincipal from(UUID userId, UUID staffId, UUID patientId, String username, String principalType,
                                      List<RoleAssignment> assignments, Instant idleExpiresAt,
                                      Instant absoluteExpiresAt, Instant now) {
        List<Assignment> effectiveAssignments = assignments.stream()
                .filter(assignment -> assignment.effectiveAt(now))
                .map(assignment -> new Assignment(assignment.assignmentId(), assignment.roleCode(),
                        assignment.permissions(), assignment.departmentId(), assignment.roomId(),
                        assignment.validFrom(), assignment.validTo()))
                .toList();
        return new UserPrincipal(userId, staffId, patientId, username, principalType, effectiveAssignments,
                idleExpiresAt, absoluteExpiresAt);
    }

    public record Assignment(UUID assignmentId, String roleCode, List<String> permissions,
                             UUID departmentId, UUID roomId, Instant validFrom, Instant validTo) {
        public Assignment {
            permissions = List.copyOf(permissions);
        }
    }
}
