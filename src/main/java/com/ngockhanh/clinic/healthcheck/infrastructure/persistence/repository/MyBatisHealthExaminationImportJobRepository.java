package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthcheck.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthcheck.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthcheck.domain.enums.ImportType;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.HealthExaminationImportJobMyBatisMapper;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationImportJobRecord;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationImportRowRecord;

@Repository
public final class MyBatisHealthExaminationImportJobRepository implements HealthExaminationImportJobRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final HealthExaminationImportJobMyBatisMapper mapper;
    private final JsonMapper objectMapper;

    public MyBatisHealthExaminationImportJobRepository(HealthExaminationImportJobMyBatisMapper mapper,
                                                        JsonMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<HealthExaminationImportJob> findById(UUID id) {
        return Optional.ofNullable(mapper.findJobById(id)).map(this::toDomain);
    }

    @Override
    public Optional<HealthExaminationImportJob> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(mapper.findJobByIdForUpdate(id)).map(this::toDomain);
    }

    @Override
    public void save(HealthExaminationImportJob job) {
        HealthExaminationImportJobRecord record = toRecord(job);
        if (mapper.updateJob(record) == 0 && mapper.insertJob(record) != 1) {
            throw new IllegalStateException("Import job was not saved");
        }
        for (HealthExaminationImportRow row : job.rows()) {
            HealthExaminationImportRowRecord rowRecord = toRecord(job.id(), row);
            if (mapper.updateRow(rowRecord) == 0 && mapper.insertRow(rowRecord) != 1) {
                throw new IllegalStateException("Import row was not saved");
            }
        }
    }

    private HealthExaminationImportJob toDomain(HealthExaminationImportJobRecord record) {
        List<HealthExaminationImportRow> rows = mapper.findRowsByJobId(record.id()).stream()
                .map(this::toDomain).toList();
        return HealthExaminationImportJob.restore(record.id(), record.healthExaminationBatchId(),
                ImportType.valueOf(record.importType()), ImportStatus.valueOf(record.status()),
                record.sourceFileAttachmentId(), record.createdByUserId(), record.createdAt(),
                record.confirmedByUserId(), record.confirmedAt(), rows);
    }

    private HealthExaminationImportRow toDomain(HealthExaminationImportRowRecord record) {
        try {
            NormalizedPayload payload = objectMapper.readValue(record.normalizedPayloadJson(), NormalizedPayload.class);
            List<String> errorCodes = record.errorCodesJson() == null ? List.of()
                    : objectMapper.readValue(record.errorCodesJson(), STRING_LIST);
            IdentificationNumber id = payload.snapshot() == null ? null : payload.snapshot().identificationNumber();
            return HealthExaminationImportRow.restore(record.id(), record.rowNumber(),
                    "VALID".equals(record.validationStatus()), errorCodes, record.participantCodeSnapshot(),
                    payload.snapshot(), payload.departmentName(), payload.jobTitle(), payload.occupation(), id,
                    record.serviceCodeSnapshot(), record.resolvedServiceRequestId(),
                    record.resolvedHealthExaminationParticipantId(), record.resolvedBatchParticipantId());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Unable to read stored import row", failure);
        }
    }

    private HealthExaminationImportJobRecord toRecord(HealthExaminationImportJob job) {
        int totalRows = job.rows().size();
        int validRows = (int) job.rows().stream().filter(HealthExaminationImportRow::valid).count();
        return new HealthExaminationImportJobRecord(job.id(), job.batchId(), job.type().name(),
                job.sourceFileAttachmentId(), job.status().name(), null, totalRows, validRows, 0,
                totalRows - validRows, job.createdByUserId(), job.confirmedByUserId(), job.createdAt(), job.confirmedAt());
    }

    private HealthExaminationImportRowRecord toRecord(UUID jobId, HealthExaminationImportRow row) {
        try {
            String normalizedPayload = objectMapper.writeValueAsString(new NormalizedPayload(
                    row.administrativeSnapshot(), row.departmentName(), row.jobTitle(), row.occupation()));
            return new HealthExaminationImportRowRecord(row.id(), jobId, row.rowNumber(), row.participantCode(),
                    row.identificationNumber() == null ? null : row.identificationNumber().value(), null,
                    row.valid() ? "VALID" : "INVALID", objectMapper.writeValueAsString(row.errorCodes()),
                    normalizedPayload, null, row.resolvedParticipantId(), row.resolvedBatchParticipantId(), null, null);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Unable to serialize import row", failure);
        }
    }

    private record NormalizedPayload(AdministrativeSnapshot snapshot, String departmentName,
                                     String jobTitle, String occupation) {
    }
}
