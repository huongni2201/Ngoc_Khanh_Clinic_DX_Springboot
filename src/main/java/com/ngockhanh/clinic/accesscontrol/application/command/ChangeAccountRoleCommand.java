package com.ngockhanh.clinic.accesscontrol.application.command;

import lombok.Builder;

/**
 * New role of a staff account (SRS UC-010 Change User Role).
 *
 * @param roleCode code of the role that replaces the current one
 * @param rowVersion version of the account the caller last read
 */
@Builder
public record ChangeAccountRoleCommand(String roleCode, Long rowVersion) {}
