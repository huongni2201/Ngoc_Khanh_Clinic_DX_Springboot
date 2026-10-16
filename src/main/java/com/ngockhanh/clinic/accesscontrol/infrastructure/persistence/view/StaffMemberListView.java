package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Row of {@code staff_members LEFT JOIN accounts} for the staff list. Component order matches the
 * SELECT column order used for constructor mapping; account columns are null without an account.
 */
public record StaffMemberListView(
    UUID id,
    String staffCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String phone,
    String email,
    String status,
    Long rowVersion,
    UUID accountId,
    String username,
    String accountStatus,
    String roleCode,
    Long accountRowVersion) {}
