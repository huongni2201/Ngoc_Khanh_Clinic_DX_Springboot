package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for creating a draft health examination agreement.
 *
 * @param configuration agreement configuration; the actor is passed separately to the use case
 */
@Builder
public record CreateHealthExaminationAgreementCommand(AgreementConfiguration configuration) {}
