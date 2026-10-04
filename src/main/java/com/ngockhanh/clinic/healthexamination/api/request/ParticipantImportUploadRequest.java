package com.ngockhanh.clinic.healthexamination.api.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ParticipantImportUploadRequest(@NotEmpty List<@NotNull UUID> selectedBatchDayIds) {}
