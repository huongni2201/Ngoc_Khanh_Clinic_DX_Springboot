package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Input for creating a draft health examination batch.
 *
 * @param configuration batch configuration; the actor is passed separately to the use case
 */
@Builder
public record CreateHealthExaminationBatchCommand(BatchConfiguration configuration) {}
