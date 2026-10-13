package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view;

import java.util.UUID;

/** SQL projection of a staff member offered as an account owner, with its department name. */
public record StaffOwnerCandidateView(
    UUID id,
    String staffCode,
    String fullName,
    String departmentName,
    String phone,
    String email) {}
