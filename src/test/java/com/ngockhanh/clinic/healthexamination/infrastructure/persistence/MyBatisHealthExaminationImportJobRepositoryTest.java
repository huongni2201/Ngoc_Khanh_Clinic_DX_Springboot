package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationImportJobMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportJobRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportRowRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisHealthExaminationImportJobRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisHealthExaminationImportJobRepositoryTest {

    @Test
    void roundTripsImportRowResolutionMetadata() {
        HealthExaminationImportJobMyBatisMapper mapper = mock(HealthExaminationImportJobMyBatisMapper.class);
        when(mapper.updateJob(any())).thenReturn(0);
        when(mapper.insertJob(any())).thenReturn(1);
        when(mapper.updateRow(any())).thenReturn(0);
        when(mapper.insertRow(any())).thenReturn(1);

        MyBatisHealthExaminationImportJobRepository repository =
                new MyBatisHealthExaminationImportJobRepository(
                        mapper, JsonMapper.builder().findAndAddModules().build());

        UUID jobId = id(1);
        HealthExaminationImportRow row = HealthExaminationImportRow.restore(
                new AggregateId(id(2)), 1, true, List.of(), "PART-01", "Nguyen A",
                LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"),
                null, null, null, null, "ORGANIZATION", null, null, null, null, null,
                null, null, null, null, null, null, "SVC-01", new AggregateId(id(6)),
                new AggregateId(id(3)), new AggregateId(id(4)), new AggregateId(id(5)),
                new AggregateId(id(7)));
        HealthExaminationImportJob job = HealthExaminationImportJob.restore(
                new AggregateId(jobId), new AggregateId(id(8)), ImportType.RESULTS,
                ImportStatus.CONFIRMED, new AggregateId(id(9)), new AggregateId(id(10)),
                Instant.parse("2026-09-29T00:00:00Z"), new AggregateId(id(11)),
                Instant.parse("2026-09-29T01:00:00Z"), List.of(row));

        repository.save(job);

        var rowCaptor = org.mockito.ArgumentCaptor.forClass(HealthExaminationImportRowRecord.class);
        verify(mapper).insertRow(rowCaptor.capture());
        HealthExaminationImportRowRecord stored = rowCaptor.getValue();
        assertThat(stored.resolvedPatientId()).isEqualTo(id(3));
        assertThat(stored.resolvedHealthExaminationParticipantId()).isEqualTo(id(4));
        assertThat(stored.resolvedBatchParticipantId()).isEqualTo(id(5));
        assertThat(stored.resolvedBatchServiceId()).isEqualTo(id(7));
        assertThat(stored.resolvedServiceRequestId()).isEqualTo(id(6));

        when(mapper.findJobById(jobId)).thenReturn(new HealthExaminationImportJobRecord(
                jobId, id(8), ImportType.RESULTS.name(), id(9), ImportStatus.CONFIRMED.name(),
                null, 1, 1, 0, 0, id(10), id(11),
                Instant.parse("2026-09-29T00:00:00Z"), Instant.parse("2026-09-29T01:00:00Z")));
        when(mapper.findRowsByJobId(jobId)).thenReturn(List.of(stored));

        HealthExaminationImportRow restored = repository.findById(new AggregateId(jobId)).orElseThrow()
                .rows().getFirst();
        assertThat(restored.resolvedPatientId()).isEqualTo(new AggregateId(id(3)));
        assertThat(restored.resolvedParticipantId()).isEqualTo(new AggregateId(id(4)));
        assertThat(restored.resolvedBatchParticipantId()).isEqualTo(new AggregateId(id(5)));
        assertThat(restored.resolvedBatchServiceId()).isEqualTo(new AggregateId(id(7)));
        assertThat(restored.serviceRequestId()).isEqualTo(new AggregateId(id(6)));
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
