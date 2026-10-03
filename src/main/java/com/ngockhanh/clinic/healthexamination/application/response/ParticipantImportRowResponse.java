package com.ngockhanh.clinic.healthexamination.application.response;

import java.time.LocalDate;
import java.util.List;

import com.ngockhanh.clinic.healthexamination.domain.enums.ImportRowAction;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;

public record ParticipantImportRowResponse(
        int rowNumber,
        String fullName,
        LocalDate dateOfBirth,
        String sex,
        String maskedIdentificationNumber,
        String phone,
        String rosterNote,
        ImportRowAction action,
        List<ParticipantImportRowErrorResponse> errors,
        List<String> warnings) {

    public static ParticipantImportRowResponse from(HealthExaminationImportRow row) {
        String id = row.getIdentificationNumber() == null ? null : row.getIdentificationNumber().value();
        String maskedId = id == null || id.length() < 4 ? null : "••••••" + id.substring(id.length() - 4);
        List<ParticipantImportRowErrorResponse> errors = row.getErrorCodes().stream()
                .map(ParticipantImportRowResponse::error).toList();
        return new ParticipantImportRowResponse(row.getRowNumber(), row.getFullName(), row.getDateOfBirth(), row.getSex(),
                maskedId, row.getPhone(), row.getRosterNote(), row.getAppliedAction(), errors, row.getWarningCodes());
    }

    private static ParticipantImportRowErrorResponse error(String code) {
        String field = switch (code) {
            case "MISSING_FULL_NAME", "VALUE_TOO_LONG_FULL_NAME" -> "fullName";
            case "INVALID_SEX" -> "sex";
            case "MISSING_DATE_OF_BIRTH", "INVALID_DATE_OF_BIRTH" -> "dateOfBirth";
            case "MISSING_IDENTIFICATION_NUMBER", "INVALID_IDENTIFICATION_NUMBER", "DUPLICATE_IN_FILE",
                    "IDENTITY_CONFLICT" -> "identificationNumber";
            default -> fieldFromLengthError(code);
        };
        String message = switch (code) {
            case "MISSING_FULL_NAME" -> "Thiếu họ và tên.";
            case "MISSING_DATE_OF_BIRTH" -> "Thiếu ngày sinh.";
            case "MISSING_IDENTIFICATION_NUMBER" -> "Thiếu CCCD.";
            case "INVALID_DATE_OF_BIRTH" -> "Ngày sinh không hợp lệ.";
            case "INVALID_IDENTIFICATION_NUMBER" -> "CCCD không hợp lệ.";
            case "INVALID_SEX" -> "Giới tính không hợp lệ.";
            case "DUPLICATE_IN_FILE" -> "CCCD bị trùng trong file.";
            case "IDENTITY_CONFLICT" -> "Thông tin định danh đang xung đột với roster đã có.";
            default -> "Giá trị vượt quá độ dài cho phép.";
        };
        return new ParticipantImportRowErrorResponse(field, code, message);
    }

    private static String fieldFromLengthError(String code) {
        if (!code.startsWith("VALUE_TOO_LONG_")) return "row";
        return switch (code.substring("VALUE_TOO_LONG_".length())) {
            case "PHONE" -> "phone";
            case "IDENTIFICATION_NUMBER_ISSUE_PLACE" -> "identificationNumberIssuePlace";
            case "ETHNICITY" -> "ethnicity";
            case "SUBJECT_TYPE" -> "subjectType";
            case "BLOOD_GROUP" -> "bloodGroup";
            case "OCCUPATION" -> "occupation";
            case "WORKPLACE" -> "workplaceOrSchool";
            case "ADDRESS" -> "addressDetail";
            case "PAYER_SOURCE" -> "payerSource";
            case "ROSTER_NOTE" -> "rosterNote";
            default -> "row";
        };
    }
}
