package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.UUID;
import lombok.Builder;

@Builder
public record CreateHealthExaminationBatchCommand(
    UUID createdBy, BatchConfigurationCommand configuration) {}
