package com.ngockhanh.clinic.accesscontrol.application.command;

import java.util.UUID;
import lombok.Builder;

/**
 * Role to create (SRS UC-008, 1.3.2 New Role).
 *
 * @param code unique code; never changes after creation
 * @param description optional description, or null
 * @param copyFromRoleId active role whose permissions the new role receives, or null
 * @param active whether the new role grants its permissions; null means active
 */
@Builder
public record CreateRoleCommand(
    String code, String name, String description, UUID copyFromRoleId, Boolean active) {}
