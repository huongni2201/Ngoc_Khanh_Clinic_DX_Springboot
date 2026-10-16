package com.ngockhanh.clinic.accesscontrol.application.command;

import lombok.Builder;

/**
 * New details of a role (SRS UC-008, 1.3.3 Edit Role). The code is not part of it: it never
 * changes.
 *
 * @param description optional description, or null
 * @param active false deactivates the role, true reactivates it
 * @param rowVersion version the caller last read
 */
@Builder
public record UpdateRoleCommand(String name, String description, Boolean active, Long rowVersion) {}
