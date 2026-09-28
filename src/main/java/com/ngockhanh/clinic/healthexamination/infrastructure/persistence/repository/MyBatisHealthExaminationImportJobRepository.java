package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationImportJobMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportJobRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportRowRecord;

@Repository
@RequiredArgsConstructor
public final class MyBatisHealthExaminationImportJobRepository implements HealthExaminationImportJobRepository {

    private final HealthExaminationImportJobMyBatisMapper mapper;
    private final JsonMapper objectMapper;

    @Override
    public Optional<HealthExaminationImportJob> findById(AggregateId id) {
        return Optional.ofNullable(mapper.findJobById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<HealthExaminationImportJob> findByIdForUpdate(AggregateId id) {
        return Optional.ofNullable(mapper.findJobByIdForUpdate(id.value())).map(this::toDomain);
    }

    @Override
    public void save(HealthExaminationImportJob job) {
        Converter converter = new Converter(objectMapper);
        HealthExaminationImportJobRecord record = converter.toRecord(job);
        if (mapper.updateJob(record) == 0 && mapper.insertJob(record) != 1) {
            throw new IllegalStateException("Import job was not saved");
        }
        for (HealthExaminationImportRow row : job.rows()) {
            HealthExaminationImportRowRecord rowRecord = converter.toRecord(job.id(), row);
            if (mapper.updateRow(rowRecord) == 0 && mapper.insertRow(rowRecord) != 1) {
                throw new IllegalStateException("Import row was not saved");
            }
        }
    }

    private HealthExaminationImportJob toDomain(HealthExaminationImportJobRecord record) {
        return new Converter(objectMapper).toDomain(record, mapper.findRowsByJobId(record.id()));
    }

    @RequiredArgsConstructor
    private static final class Converter {
        private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
        private final JsonMapper objectMapper;

        HealthExaminationImportJob toDomain(HealthExaminationImportJobRecord record, List<HealthExaminationImportRowRecord> rowRecords) {
            List<HealthExaminationImportRow> rows = rowRecords.stream()
                    .map(this::toDomain).toList();
            return HealthExaminationImportJob.restore(new AggregateId(record.id()),
                    new AggregateId(record.healthExaminationBatchId()), ImportType.valueOf(record.importType()),
                    ImportStatus.valueOf(record.status()),
                    record.sourceFileAttachmentId() == null ? null : new AggregateId(record.sourceFileAttachmentId()),
                    record.createdByUserId() == null ? null : new AggregateId(record.createdByUserId()),
                    record.createdAt(), record.confirmedByUserId() == null ? null : new AggregateId(record.confirmedByUserId()),
                    record.confirmedAt(), rows);
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
                return HealthExaminationImportRow.restore(new AggregateId(record.id()), record.rowNumber(),
                        "VALID".equals(record.validationStatus()), errorCodes, record.participantCodeSnapshot(),
                        payload.fullName(), payload.dateOfBirth(), payload.sex(), identificationNumber,
                        payload.identificationNumberIssueDate(), payload.identificationNumberIssuePlace(),
                        payload.ethnicity(), payload.subjectType(), payload.payerSource(), payload.bloodGroup(),
                        payload.phone(), payload.province(), payload.ward(), payload.addressDetail(),
                        payload.administrativeOccupation(), payload.workplaceOrSchool(),
                        payload.healthExaminationReason(), payload.departmentName(), payload.jobTitle(),
                        payload.occupation(), record.serviceCodeSnapshot(), toId(record.resolvedServiceRequestId()),
                        toId(record.resolvedHealthExaminationParticipantId()), toId(record.resolvedBatchParticipantId()));
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Unable to read stored import row", failure);
            }
        }

        private HealthExaminationImportJobRecord toRecord(HealthExaminationImportJob job) {
            int totalRows = job.rows().size();
            int validRows = (int) job.rows().stream().filter(HealthExaminationImportRow::valid).count();
            return new HealthExaminationImportJobRecord(job.id().value(), job.batchId().value(), job.type().name(),
                    value(job.sourceFileAttachmentId()), job.status().name(), null, totalRows, validRows, 0,
                    totalRows - validRows, value(job.createdByUserId()), value(job.confirmedByUserId()),
                    job.createdAt(), job.confirmedAt());
        }

        private HealthExaminationImportRowRecord toRecord(AggregateId jobId, HealthExaminationImportRow row) {
            try {
                String normalizedPayload = objectMapper.writeValueAsString(new NormalizedPayload(
                        row.fullName(), row.dateOfBirth(), row.sex(),
                        row.identificationNumber() == null ? null : row.identificationNumber().value(),
                        row.identificationNumberIssueDate(), row.identificationNumberIssuePlace(), row.ethnicity(),
                        row.subjectType(), row.payerSource(), row.bloodGroup(), row.phone(), row.province(), row.ward(),
                        row.addressDetail(), row.administrativeOccupation(), row.workplaceOrSchool(),
                        row.healthExaminationReason(), row.departmentName(), row.jobTitle(), row.occupation()));
                return new HealthExaminationImportRowRecord(row.id().value(), jobId.value(), row.rowNumber(),
                        row.participantCode(), row.identificationNumber() == null ? null : row.identificationNumber().value(),
                        row.serviceCode(), row.valid() ? "VALID" : "INVALID",
                        objectMapper.writeValueAsString(row.errorCodes()), normalizedPayload, null,
                        value(row.resolvedParticipantId()), value(row.resolvedBatchParticipantId()), null,
                        value(row.serviceRequestId()));
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
                String occupation) {
        }
    }
}
