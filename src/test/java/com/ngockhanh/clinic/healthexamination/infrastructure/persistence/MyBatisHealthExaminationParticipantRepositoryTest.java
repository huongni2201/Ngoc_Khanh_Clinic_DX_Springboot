package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisHealthExaminationParticipantRepository;

class MyBatisHealthExaminationParticipantRepositoryTest {
    @Test
    void savesParticipantRosterInOneBulkMapperCall() {
        HealthExaminationParticipantMyBatisMapper mapper = mock(HealthExaminationParticipantMyBatisMapper.class);
        when(mapper.upsertParticipants(any(Collection.class))).thenReturn(2);
        MyBatisHealthExaminationParticipantRepository repository =
                new MyBatisHealthExaminationParticipantRepository(mapper);
        List<HealthExaminationParticipant> participants = List.of(
                participant(1, "000000000001", "Test One"), participant(2, "000000000002", "Test Two"));

        repository.saveAll(participants);

        var captor = org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(mapper).upsertParticipants(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        HealthExaminationParticipantRecord first = (HealthExaminationParticipantRecord)
                ((Collection<?>) captor.getValue()).iterator().next();
        assertThat(first.participantCode()).isEqualTo(first.id().toString());
    }

    private static HealthExaminationParticipant participant(long id, String cccd, String name) {
        AggregateId aggregateId = AggregateId.of(new UUID(0, id));
        return HealthExaminationParticipant.create(aggregateId, AggregateId.of(new UUID(0, 7)),
                aggregateId.value().toString(), IdentificationNumber.of(cccd), name,
                LocalDate.of(1990, 1, 1), "MALE");
    }
}
