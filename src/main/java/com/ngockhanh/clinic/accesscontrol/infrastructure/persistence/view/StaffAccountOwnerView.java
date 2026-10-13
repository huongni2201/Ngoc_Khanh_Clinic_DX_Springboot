package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view;

import java.util.UUID;

/** SQL projection of a staff member as needed to link an account. */
public record StaffAccountOwnerView(UUID id, String status, String phone) {}
