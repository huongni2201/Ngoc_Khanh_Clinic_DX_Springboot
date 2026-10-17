package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for ending an active health examination agreement (ACTIVE to ENDED).
 *
 * @param rowVersion header version the caller last read; must match the stored version
 */
@Builder
public record EndHealthExaminationAgreementCommand(Long rowVersion) {}
