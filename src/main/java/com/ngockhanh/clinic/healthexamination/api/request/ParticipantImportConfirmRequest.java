package com.ngockhanh.clinic.healthexamination.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ParticipantImportConfirmRequest(@NotNull @PositiveOrZero Long expectedRowVersion) {}
