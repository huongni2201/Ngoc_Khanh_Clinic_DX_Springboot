package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection;

import java.util.UUID;

/**
 * Sign-in row of {@code accounts LEFT JOIN staff_members}. Component order matches the SELECT
 * column order used for constructor mapping.
 */
public record UserLoginRow(
    UUID accountId,
    String accountType,
    String username,
    String passwordHash,
    String status,
    UUID staffMemberId,
    String staffStatus,
    UUID patientId) {}
