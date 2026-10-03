package com.ngockhanh.clinic.healthexamination.api.request;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

public record ParticipantImportMappingRequest(
        @NotNull Map<String, Integer> columns) {
}
