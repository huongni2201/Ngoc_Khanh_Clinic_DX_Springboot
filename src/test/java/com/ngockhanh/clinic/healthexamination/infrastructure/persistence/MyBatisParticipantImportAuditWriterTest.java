package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter.AuditEntry;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationImportJobMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.ImportAuditLogRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisParticipantImportAuditWriter;

class MyBatisParticipantImportAuditWriterTest {
    @Test
    void writesTheActorAndStatusTransitionWithoutRosterData() {
        HealthExaminationImportJobMyBatisMapper mapper = mock(HealthExaminationImportJobMyBatisMapper.class);
        when(mapper.insertImportAudit(any())).thenReturn(1);
        MyBatisParticipantImportAuditWriter writer = new MyBatisParticipantImportAuditWriter(mapper);
        AuditEntry entry = new AuditEntry(id(1), id(2), Instant.parse("2026-09-30T00:00:00Z"),
                "PARTICIPANT_ROSTER_IMPORT_CONFIRMED", id(3),
                "{\"status\":\"VALIDATED\"}", "{\"status\":\"CONFIRMED\",\"totalRows\":1}");

        writer.record(entry);

        var captor = org.mockito.ArgumentCaptor.forClass(ImportAuditLogRecord.class);
        verify(mapper).insertImportAudit(captor.capture());
        assertThat(captor.getValue().actorUserId()).isEqualTo(id(2));
        assertThat(captor.getValue().entityId()).isEqualTo(id(3).toString());
        assertThat(captor.getValue().afterJson()).contains("CONFIRMED").doesNotContain("CCCD");
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
