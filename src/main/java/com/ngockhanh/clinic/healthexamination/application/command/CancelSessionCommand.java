package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for cancelling a session nobody is assigned to.
 *
 * @param rowVersion session version the caller last read; must match the stored version
 */
@Builder
public record CancelSessionCommand(Long rowVersion) {}
