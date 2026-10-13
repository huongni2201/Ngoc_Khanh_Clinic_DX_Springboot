package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for starting a draft health examination batch (DRAFT to READY).
 *
 * @param rowVersion header version the caller last read; must match the stored version
 */
@Builder
public record StartHealthExaminationBatchCommand(Long rowVersion) {}
