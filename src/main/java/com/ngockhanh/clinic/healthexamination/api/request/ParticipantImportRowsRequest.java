package com.ngockhanh.clinic.healthexamination.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ParticipantImportRowsRequest(
    @Min(1) Integer page, @Min(1) @Max(100) Integer size, String status) {}
