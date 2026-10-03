package com.ngockhanh.clinic.healthexamination.application.response;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository.ImportJobSummary;

public record ParticipantImportSummaryResponse(
        UUID importId,
        String status,
        int totalRows,
        int validRows,
        int warningRows,
        int errorRows,
        boolean confirmAllowed,
        Map<ParticipantImportField, Integer> columnMapping,
        List<String> headers) {

    public static ParticipantImportSummaryResponse from(HealthExaminationImportJob job, List<String> headers) {
        return from(job, headers, Map.of());
    }

    public static ParticipantImportSummaryResponse from(HealthExaminationImportJob job, List<String> headers,
                                                          Map<ParticipantImportField, Integer> suggestedMapping) {
        List<HealthExaminationImportRow> rows = job.rows();
        int total = rows.size();
        int valid = (int) rows.stream().filter(HealthExaminationImportRow::isValid).count();
        int warnings = (int) rows.stream().filter(row -> !row.getWarningCodes().isEmpty()).count();
        int errors = total - valid;
        Map<ParticipantImportField, Integer> mapping = job.columnMapping() == null
                ? Map.copyOf(suggestedMapping) : job.columnMapping().columns();
        return new ParticipantImportSummaryResponse(job.id().value(), job.status().name(), total, valid,
                warnings, errors, job.status() == ImportStatus.VALIDATED && total > 0 && errors == 0,
                mapping, headers == null ? List.of() : List.copyOf(headers));
    }

    public static ParticipantImportSummaryResponse from(ImportJobSummary job, List<String> headers,
                                                          Map<ParticipantImportField, Integer> suggestedMapping) {
        Map<ParticipantImportField, Integer> mapping = job.columnMapping() == null
                ? Map.copyOf(suggestedMapping) : job.columnMapping().columns();
        return new ParticipantImportSummaryResponse(job.id().value(), job.status().name(), job.totalRows(),
                job.validRows(), job.warningRows(), job.errorRows(),
                job.status() == ImportStatus.VALIDATED && job.totalRows() > 0 && job.errorRows() == 0,
                mapping, headers == null ? List.of() : List.copyOf(headers));
    }
}
