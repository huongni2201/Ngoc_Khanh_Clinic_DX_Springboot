package com.ngockhanh.clinic.healthcheck.application.importparticipant;

public record ImportValidationError(int rowNumber, String field, String code, String message) {
}
