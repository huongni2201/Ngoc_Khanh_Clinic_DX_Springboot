package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisHealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MyBatisHealthExaminationBatchParticipantRepositoryTest {
  private HealthExaminationBatchParticipant participant() {
    return HealthExaminationBatchParticipant.create(
        id(10),
        id(1),
        id(3),
        new Roster(
            null,
            "Synthetic Person",
            LocalDate.of(1990, 1, 1),
            "MALE",
            IdentificationNumber.of("000000000001"),
            null,
            null,
            "Department",
            "Position"),
        null,
        null,
        NOW);
  }

  @Test
  void detectsConcurrentHeaderUpdateBeforeWritingServiceRows() {
    var mapper = mock(HealthExaminationBatchParticipantMyBatisMapper.class);
    var repository = new MyBatisHealthExaminationBatchParticipantRepository(mapper);
    assertThatThrownBy(() -> repository.save(participant(), 0))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(mapper, never()).insertServices(any());
  }

  @Test
  void persistsReconciledServicesAlongsideHeader() {
    var mapper = mock(HealthExaminationBatchParticipantMyBatisMapper.class);
    when(mapper.update(any(), eq(0L))).thenReturn(1);
    when(mapper.insertServices(any())).thenReturn(1);
    var repository = new MyBatisHealthExaminationBatchParticipantRepository(mapper);
    var p = participant();
    var s =
        new HealthExaminationBatchParticipantService(
            id(11), id(1), id(10), id(12), true, null, Money.vnd("100"), id(6), NOW, NOW, NOW, 0);
    p.reconcileServices(List.of(s), scope(id(12)), id(6), NOW);
    repository.save(p, 0);
    verify(mapper)
        .insertServices(
            argThat(
                rows ->
                    rows.size() == 1
                        && rows.getFirst().isPerformed()
                        && rows.getFirst().batchParticipantId().equals(id(10).value())));
  }
}
