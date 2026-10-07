package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for soft-deleting a draft health examination batch.
 *
 * @param rowVersion header version the caller last read; must match the stored version
 */
@Builder
public record DeleteHealthExaminationBatchCommand(Long rowVersion) {}
