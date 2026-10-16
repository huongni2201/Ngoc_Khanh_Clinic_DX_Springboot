package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for activating a draft health examination agreement (DRAFT to ACTIVE).
 *
 * @param rowVersion header version the caller last read; must match the stored version
 */
@Builder
public record ActivateHealthExaminationAgreementCommand(Long rowVersion) {}
