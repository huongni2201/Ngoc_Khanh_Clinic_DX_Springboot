package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for deactivating an organization.
 *
 * @param rowVersion version the caller last read; must match the stored version
 */
@Builder
public record DeleteOrganizationCommand(Long rowVersion) {}
