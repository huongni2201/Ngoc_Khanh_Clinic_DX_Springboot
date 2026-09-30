package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportRowAction;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationImportJobMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportJobRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportRowRecord;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationImportJobRepository implements HealthExaminationImportJobRepository {

    private final HealthExaminationImportJobMyBatisMapper mapper;
    private final JsonMapper objectMapper;

    @Override
    public Optional<HealthExaminationImportJob> findByIdAndBatchId(AggregateId importId, AggregateId batchId) {
        return Optional.ofNullable(mapper.findJobByIdAndBatchId(importId.value(), batchId.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<HealthExaminationImportJob> findByIdAndBatchIdForUpdate(
            AggregateId importId, AggregateId batchId) {
        return Optional.ofNullable(mapper.findJobByIdAndBatchIdForUpdate(importId.value(), batchId.value()))
                .map(this::toDomain);
    }

    @Override
    public void save(HealthExaminationImportJob job) {
        Converter converter = new Converter(objectMapper);
        HealthExaminationImportJobRecord record = converter.toRecord(job);
        if (mapper.updateJob(record) == 0 && mapper.insertJob(record) != 1) {
            throw new IllegalStateException("Import job was not saved");
        }
        List<HealthExaminationImportRowRecord> rows = job.rows().stream()
                .map(row -> converter.toRecord(job.id(), row)).toList();
        for (int start = 0; start < rows.size(); start += 500) {
            List<HealthExaminationImportRowRecord> chunk = rows.subList(start, Math.min(start + 500, rows.size()));
            if (mapper.upsertRows(chunk) != chunk.size()) {
                throw new IllegalStateException("Import rows were not saved");
            }
        }
    }

    private HealthExaminationImportJob toDomain(HealthExaminationImportJobRecord record) {
        return new Converter(objectMapper).toDomain(record, mapper.findRowsByJobId(record.id()));
    }

    @RequiredArgsConstructor
    private static final class Converter {
        private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
        private static final TypeReference<Map<ParticipantImportField, Integer>> COLUMN_MAPPING = new TypeReference<>() { };
        private final JsonMapper objectMapper;

        HealthExaminationImportJob toDomain(HealthExaminationImportJobRecord record, List<HealthExaminationImportRowRecord> rowRecords) {
            List<HealthExaminationImportRow> rows = rowRecords.stream()
                    .map(this::toDomain).toList();
            ParticipantImportColumnMapping mapping = record.columnMappingJson() == null
                    ? null : mappingFromJson(record.columnMappingJson());
            return HealthExaminationImportJob.restore(new AggregateId(record.id()),
                    new AggregateId(record.healthExaminationBatchId()), ImportType.valueOf(record.importType()),
                    ImportStatus.valueOf(record.status()),
                    record.sourceFileAttachmentId() == null ? null : new AggregateId(record.sourceFileAttachmentId()),
                    record.createdByUserId() == null ? null : new AggregateId(record.createdByUserId()),
                    record.createdAt(), record.confirmedByUserId() == null ? null : new AggregateId(record.confirmedByUserId()),
                    record.confirmedAt(), mapping, rows);
        }

        private HealthExaminationImportRow toDomain(HealthExaminationImportRowRecord record) {
            try {
                NormalizedPayload payload = objectMapper.readValue(record.normalizedPayloadJson(), NormalizedPayload.class);
                List<String> errorCodes = record.errorCodesJson() == null ? List.of()
                        : objectMapper.readValue(record.errorCodesJson(), STRING_LIST);
                String identificationValue = payload.identificationNumber() == null
                        ? record.identificationNumberSnapshot() : payload.identificationNumber();
                IdentificationNumber identificationNumber = identificationValue == null
                        ? null : IdentificationNumber.of(identificationValue);
                HealthExaminationImportRow row = HealthExaminationImportRow.restore(new AggregateId(record.id()), record.rowNumber(),
                        "VALID".equals(record.validationStatus()), errorCodes, record.participantCodeSnapshot(),
                        payload.fullName(), payload.dateOfBirth(), payload.sex(), identificationNumber,
                        payload.identificationNumberIssueDate(), payload.identificationNumberIssuePlace(),
                        payload.ethnicity(), payload.subjectType(), payload.payerSource(), payload.bloodGroup(),
                        payload.phone(), payload.province(), payload.ward(), payload.addressDetail(),
                        payload.administrativeOccupation(), payload.workplaceOrSchool(),
                        payload.healthExaminationReason(), payload.departmentName(), payload.jobTitle(),
                        payload.occupation(), record.serviceCodeSnapshot(), toId(record.resolvedServiceRequestId()),
                        toId(record.resolvedPatientId()), toId(record.resolvedHealthExaminationParticipantId()),
                        toId(record.resolvedBatchParticipantId()), toId(record.resolvedBatchServiceId()));
                if (payload.rosterNote() != null) row.setRosterNote(payload.rosterNote());
                if (payload.warningCodes() != null) payload.warningCodes().forEach(row::addWarning);
                if (payload.appliedAction() != null) row.setAppliedAction(payload.appliedAction());
                row.setPreviewFingerprint(payload.previewFingerprint());
                return row;
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Unable to read stored import row", failure);
            }
        }

        private HealthExaminationImportJobRecord toRecord(HealthExaminationImportJob job) {
            List<HealthExaminationImportRow> rows = job.rows();
            int totalRows = rows.size();
            int validRows = (int) rows.stream().filter(HealthExaminationImportRow::isValid).count();
            int warningRows = (int) rows.stream().filter(row -> !row.getWarningCodes().isEmpty()).count();
            try {
                String mappingJson = job.columnMapping() == null ? null
                        : objectMapper.writeValueAsString(job.columnMapping().columns());
                return new HealthExaminationImportJobRecord(job.id().value(), job.batchId().value(), job.type().name(),
                        value(job.sourceFileAttachmentId()), job.status().name(), mappingJson, totalRows, validRows, warningRows,
                        totalRows - validRows, value(job.createdByUserId()), value(job.confirmedByUserId()),
                        job.createdAt(), job.confirmedAt());
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Unable to serialize import column mapping", failure);
            }
        }

        private HealthExaminationImportRowRecord toRecord(AggregateId jobId, HealthExaminationImportRow row) {
            try {
                String normalizedPayload = objectMapper.writeValueAsString(new NormalizedPayload(
                        row.getFullName(), row.getDateOfBirth(), row.getSex(),
                        row.getIdentificationNumber() == null ? null : row.getIdentificationNumber().value(),
                        row.getIdentificationNumberIssueDate(), row.getIdentificationNumberIssuePlace(), row.getEthnicity(),
                        row.getSubjectType(), row.getPayerSource(), row.getBloodGroup(), row.getPhone(), row.getProvince(), row.getWard(),
                        row.getAddressDetail(), row.getAdministrativeOccupation(), row.getWorkplaceOrSchool(),
                        row.getHealthExaminationReason(), row.getDepartmentName(), row.getJobTitle(), row.getOccupation(),
                        row.getRosterNote(), row.getWarningCodes(), row.getAppliedAction(), row.getPreviewFingerprint()));
                return new HealthExaminationImportRowRecord(row.getId().value(), jobId.value(), row.getRowNumber(),
                        row.getParticipantCode(), row.getIdentificationNumber() == null ? null : row.getIdentificationNumber().value(),
                        row.getServiceCode(), row.isValid() ? "VALID" : "INVALID",
                        objectMapper.writeValueAsString(row.getErrorCodes()), normalizedPayload,
                        value(row.getResolvedPatientId()), value(row.getResolvedParticipantId()),
                        value(row.getResolvedBatchParticipantId()), value(row.getResolvedBatchServiceId()),
                        value(row.getServiceRequestId()));
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Unable to serialize import row", failure);
            }
        }

        private static AggregateId toId(java.util.UUID value) {
            return value == null ? null : new AggregateId(value);
        }

        private static java.util.UUID value(AggregateId id) {
            return id == null ? null : id.value();
        }

        private ParticipantImportColumnMapping mappingFromJson(String json) {
            try {
                return ParticipantImportColumnMapping.of(objectMapper.readValue(json, COLUMN_MAPPING));
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Unable to read stored import column mapping", failure);
            }
        }

        private record NormalizedPayload(
                String fullName,
                LocalDate dateOfBirth,
                String sex,
                String identificationNumber,
                LocalDate identificationNumberIssueDate,
                String identificationNumberIssuePlace,
                String ethnicity,
                String subjectType,
                String payerSource,
                String bloodGroup,
                String phone,
                String province,
                String ward,
                String addressDetail,
                String administrativeOccupation,
                String workplaceOrSchool,
                String healthExaminationReason,
                String departmentName,
                String jobTitle,
                String occupation,
                String rosterNote,
                List<String> warningCodes,
                ImportRowAction appliedAction,
                String previewFingerprint) {
        }
    }
}
