package com.ngockhanh.clinic.healthexamination.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ParticipantImportCancelRequest(@NotNull @PositiveOrZero Long expectedRowVersion) {}
