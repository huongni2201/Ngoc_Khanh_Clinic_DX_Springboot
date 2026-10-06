package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for replacing the configuration of a draft health examination batch.
 *
 * @param configuration full replacement configuration
 * @param rowVersion header version the caller last read; must match the stored version
 */
@Builder
public record UpdateHealthExaminationBatchCommand(
    BatchConfiguration configuration, Long rowVersion) {}
