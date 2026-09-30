package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.UUID;

public record CreateHealthExaminationBatchCommand(
    UUID createdBy, BatchConfigurationCommand configuration) {}
