package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository.BatchParticipantRosterSnapshot;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.BatchParticipantSummaryMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantServiceRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisHealthExaminationBatchParticipantRepository;

class MyBatisHealthExaminationBatchParticipantRepositoryTest {
    @Test
    void bulkInsertsNewRosterSnapshots() {
        HealthExaminationBatchParticipantMyBatisMapper mapper = mock(HealthExaminationBatchParticipantMyBatisMapper.class);
        BatchParticipantSummaryMyBatisMapper summaryMapper = mock(BatchParticipantSummaryMyBatisMapper.class);
        when(mapper.insertRosterSnapshots(org.mockito.ArgumentMatchers.eq(id(2).value()),
                org.mockito.ArgumentMatchers.any(Collection.class))).thenReturn(1);
        MyBatisHealthExaminationBatchParticipantRepository repository =
                new MyBatisHealthExaminationBatchParticipantRepository(mapper, summaryMapper);
        BatchParticipantRosterSnapshot snapshot = new BatchParticipantRosterSnapshot(
                id(10), id(3), "technical-code", null, null, "Nghề nghiệp", "Test Person",
                LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"),
                null, null, null, null, null, null, null, null, null, "Address", null,
                "Workplace", null, "Roster note");

        repository.insertRosterSnapshots(id(2), List.of(snapshot));

        verify(mapper).insertRosterSnapshots(org.mockito.ArgumentMatchers.eq(id(2).value()),
                org.mockito.ArgumentMatchers.argThat(items -> {
            HealthExaminationBatchParticipantRecord stored = items.iterator().next();
            return stored.healthExaminationBatchId().equals(id(2).value())
                    && stored.rosterNoteSnapshot().equals("Roster note");
        }));
    }

    @Test
    void bulkUpdatesOnlyTheExistingSnapshotIdentity() {
        HealthExaminationBatchParticipantMyBatisMapper mapper = mock(HealthExaminationBatchParticipantMyBatisMapper.class);
        BatchParticipantSummaryMyBatisMapper summaryMapper = mock(BatchParticipantSummaryMyBatisMapper.class);
        when(mapper.updateRosterSnapshots(org.mockito.ArgumentMatchers.eq(id(2).value()),
                org.mockito.ArgumentMatchers.any(Collection.class))).thenReturn(1);
        MyBatisHealthExaminationBatchParticipantRepository repository =
                new MyBatisHealthExaminationBatchParticipantRepository(mapper, summaryMapper);
        BatchParticipantRosterSnapshot snapshot = new BatchParticipantRosterSnapshot(
                id(10), id(3), "technical-code", null, null, "Nghề nghiệp", "Updated Person",
                LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"),
                null, null, null, null, null, null, null, null, null, "Address", null,
                "Workplace", null, "Updated note");

        repository.updateRosterSnapshots(id(2), List.of(snapshot));

        verify(mapper).updateRosterSnapshots(org.mockito.ArgumentMatchers.eq(id(2).value()),
                org.mockito.ArgumentMatchers.argThat(items -> {
                    HealthExaminationBatchParticipantRecord stored = items.iterator().next();
                    return stored.id().equals(id(10).value())
                            && stored.healthExaminationBatchId().equals(id(2).value())
                            && stored.healthExaminationParticipantId().equals(id(3).value())
                            && stored.rosterNoteSnapshot().equals("Updated note");
                }));
    }

    @Test
    void updatesRosterSnapshotWithoutReplacingAssignments() {
        HealthExaminationBatchParticipantMyBatisMapper mapper = mock(HealthExaminationBatchParticipantMyBatisMapper.class);
        BatchParticipantSummaryMyBatisMapper summaryMapper = mock(BatchParticipantSummaryMyBatisMapper.class);
        when(mapper.updateRosterSnapshot(org.mockito.ArgumentMatchers.any(HealthExaminationBatchParticipantRecord.class)))
                .thenReturn(1);

        MyBatisHealthExaminationBatchParticipantRepository repository =
                new MyBatisHealthExaminationBatchParticipantRepository(mapper, summaryMapper);
        HealthExaminationBatchParticipant participant = HealthExaminationBatchParticipant.create(
                id(1), id(2), id(3), "Nguyen A", LocalDate.of(1990, 1, 1), "MALE",
                IdentificationNumber.of("012345678901"));
        participant.assignService(id(4), id(5), id(6), Money.vnd("90000"));
        participant.updateRosterNoteSnapshot("Updated roster note");

        repository.updateRosterSnapshot(participant);

        var recordCaptor = org.mockito.ArgumentCaptor.forClass(HealthExaminationBatchParticipantRecord.class);
        verify(mapper).updateRosterSnapshot(recordCaptor.capture());
        assertThat(recordCaptor.getValue().rosterNoteSnapshot()).isEqualTo("Updated roster note");
        verify(mapper, never()).insertAssignments(org.mockito.ArgumentMatchers.any(Collection.class));
    }

    @Test
    void savesOwnedServiceAssignmentsWithTheBatchParticipant() {
        HealthExaminationBatchParticipantMyBatisMapper mapper = mock(HealthExaminationBatchParticipantMyBatisMapper.class);
        BatchParticipantSummaryMyBatisMapper summaryMapper = mock(BatchParticipantSummaryMyBatisMapper.class);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(HealthExaminationBatchParticipantRecord.class))).thenReturn(1);
        when(mapper.insertAssignments(org.mockito.ArgumentMatchers.any(Collection.class))).thenReturn(1);

        MyBatisHealthExaminationBatchParticipantRepository repository =
                new MyBatisHealthExaminationBatchParticipantRepository(mapper, summaryMapper);
        HealthExaminationBatchParticipant participant = HealthExaminationBatchParticipant.create(
                id(1), id(2), id(3), "Nguyen A", LocalDate.of(1990, 1, 1), "MALE",
                IdentificationNumber.of("012345678901"));
        participant.assignService(id(4), id(5), id(6), Money.vnd("90000"));
        participant.updateRosterNoteSnapshot("Fixture note");

        repository.save(participant);

        var recordCaptor = org.mockito.ArgumentCaptor.forClass(HealthExaminationBatchParticipantRecord.class);
        verify(mapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().rosterNoteSnapshot()).isEqualTo("Fixture note");

        verify(mapper).insertAssignments(List.of(new HealthExaminationBatchParticipantServiceRecord(
                id(4).value(), id(1).value(), id(5).value(), id(6).value(), false,
                Money.vnd("90000").amount(), null, null)));
    }

    private static AggregateId id(int value) {
        return AggregateId.of(new java.util.UUID(0L, value));
    }
}
