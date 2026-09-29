package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.BatchParticipantSummaryMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantServiceRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisHealthExaminationBatchParticipantRepository;

class MyBatisHealthExaminationBatchParticipantRepositoryTest {
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

        repository.save(participant);

        verify(mapper).insertAssignments(List.of(new HealthExaminationBatchParticipantServiceRecord(
                id(4).value(), id(1).value(), id(5).value(), id(6).value(), false,
                Money.vnd("90000").amount(), null, null)));
    }

    private static AggregateId id(int value) {
        return AggregateId.of(new java.util.UUID(0L, value));
    }
}
