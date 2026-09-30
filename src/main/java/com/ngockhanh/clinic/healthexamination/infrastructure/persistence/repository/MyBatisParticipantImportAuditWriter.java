package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter.AuditEntry;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationImportJobMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.ImportAuditLogRecord;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MyBatisParticipantImportAuditWriter implements ParticipantImportAuditWriter {
    private final HealthExaminationImportJobMyBatisMapper mapper;

    @Override
    public void record(AuditEntry entry) {
        ImportAuditLogRecord record = new ImportAuditLogRecord(entry.id(), entry.occurredAt(), entry.actorUserId(),
                entry.action(), entry.importJobId().toString(), entry.beforeJson(), entry.afterJson());
        if (mapper.insertImportAudit(record) != 1) throw new IllegalStateException("Import audit was not saved");
    }
}
