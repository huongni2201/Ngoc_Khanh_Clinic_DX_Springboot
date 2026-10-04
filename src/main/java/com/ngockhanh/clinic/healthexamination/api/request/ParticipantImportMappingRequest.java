package com.ngockhanh.clinic.healthexamination.api.request;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A frozen day selection and optional individual preview assignments. */
public record ParticipantImportMappingRequest(
    @NotEmpty List<@NotNull UUID> selectedBatchDayIds,
    @NotNull @PositiveOrZero Long expectedRowVersion,
    Map<@Positive Integer, @NotNull UUID> rowAssignments) {}
