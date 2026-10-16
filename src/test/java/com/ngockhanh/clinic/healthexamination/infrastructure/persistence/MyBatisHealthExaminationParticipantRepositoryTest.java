package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisHealthExaminationParticipantRepository;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MyBatisHealthExaminationParticipantRepositoryTest {
  private HealthExaminationParticipant participant() {
    return HealthExaminationParticipant.create(
        id(10),
        id(2),
        id(1),
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
    var mapper = mock(HealthExaminationParticipantMyBatisMapper.class);
    var repository = new MyBatisHealthExaminationParticipantRepository(mapper);
    assertThatThrownBy(() -> repository.save(participant(), 0))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(mapper, never()).insertServices(any());
  }

  @Test
  void saveWithoutServicesUpdatesTheHeaderAndNeverTouchesServiceRows() {
    var mapper = mock(HealthExaminationParticipantMyBatisMapper.class);
    when(mapper.update(any(), eq(0L))).thenReturn(1);
    var repository = new MyBatisHealthExaminationParticipantRepository(mapper);
    var p = participant();
    var s =
        new HealthExaminationParticipantService(
            id(11), id(1), id(10), id(12), true, null, Money.vnd("100"), id(6), NOW, NOW, NOW, 0);
    p.reconcileServices(List.of(s), scope(id(12)), id(6), NOW);

    repository.saveWithoutServices(p, 0);

    verify(mapper).update(any(), eq(0L));
    verify(mapper, never()).findServices(any());
    verify(mapper, never()).insertServices(any());
    verify(mapper, never()).updateServices(any());
  }

  @Test
  void saveWithoutServicesDetectsAConcurrentHeaderUpdate() {
    var mapper = mock(HealthExaminationParticipantMyBatisMapper.class);
    var repository = new MyBatisHealthExaminationParticipantRepository(mapper);
    assertThatThrownBy(() -> repository.saveWithoutServices(participant(), 0))
        .isInstanceOf(ConcurrentUpdateException.class);
  }

  @Test
  void persistsReconciledServicesAlongsideHeader() {
    var mapper = mock(HealthExaminationParticipantMyBatisMapper.class);
    when(mapper.update(any(), eq(0L))).thenReturn(1);
    when(mapper.insertServices(any())).thenReturn(1);
    var repository = new MyBatisHealthExaminationParticipantRepository(mapper);
    var p = participant();
    var s =
        new HealthExaminationParticipantService(
            id(11), id(1), id(10), id(12), true, null, Money.vnd("100"), id(6), NOW, NOW, NOW, 0);
    p.reconcileServices(List.of(s), scope(id(12)), id(6), NOW);
    repository.save(p, 0);
    verify(mapper)
        .insertServices(
            argThat(
                rows ->
                    rows.size() == 1
                        && rows.getFirst().isPerformed()
                        && rows.getFirst().participantId().equals(id(10).value())));
  }
}
