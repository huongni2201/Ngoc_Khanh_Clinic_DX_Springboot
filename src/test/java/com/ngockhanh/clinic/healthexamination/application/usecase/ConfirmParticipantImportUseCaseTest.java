package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.*;
import org.junit.jupiter.api.*;

class ConfirmParticipantImportUseCaseTest {
  final HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
  final HealthExaminationImportJobRepository jobs =
      mock(HealthExaminationImportJobRepository.class);
  final HealthExaminationBatchParticipantRepository participants =
      mock(HealthExaminationBatchParticipantRepository.class);
  final ParticipantImportAuditWriter audit = mock(ParticipantImportAuditWriter.class);
  final ConfirmParticipantImportUseCase usecase =
      new ConfirmParticipantImportUseCase(CLOCK, batches, jobs, participants, audit);

  @BeforeEach
  void setup() {
    when(batches.findByIdAndOrganizationIdForUpdate(id(1), id(2))).thenReturn(Optional.of(batch()));
  }

  @Test
  void insertsOnlyNewRowsAndRetryReturnsSavedResultWithoutNewWrites() {
    var job = job(0);
    when(jobs.findByIdAndBatchIdForUpdate(id(5), id(1))).thenReturn(Optional.of(job));
    var first = usecase.execute(id(2).value(), id(1).value(), id(5).value(), id(6).value(), 0);
    assertThat(first.importedRows()).isEqualTo(1);
    assertThat(job.rows().getFirst().getBatchDayId()).isEqualTo(id(3));
    var retry = usecase.execute(id(2).value(), id(1).value(), id(5).value(), id(6).value(), 999);
    assertThat(retry).isEqualTo(first);
    verify(participants, times(1)).insertAll(any());
    verify(jobs, times(1)).save(job);
    verify(audit, times(1)).record(any());
    verify(participants, never()).activeCountsByDay(any());
  }

  @Test
  void conflictingIdentificationNumberRejectsAtomicConfirmation() {
    var job = job(0);
    when(jobs.findByIdAndBatchIdForUpdate(id(5), id(1))).thenReturn(Optional.of(job));
    when(participants.existingIdentificationNumbers(any(), any()))
        .thenReturn(Set.of(IdentificationNumber.of("000000000001")));
    assertThatThrownBy(
            () -> usecase.execute(id(2).value(), id(1).value(), id(5).value(), id(6).value(), 0))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(participants, never()).insertAll(any());
    verify(jobs, never()).save(any());
    verifyNoInteractions(audit);
  }

  @Test
  void staleVersionCannotCommit() {
    when(jobs.findByIdAndBatchIdForUpdate(id(5), id(1))).thenReturn(Optional.of(job(1)));
    assertThatThrownBy(
            () -> usecase.execute(id(2).value(), id(1).value(), id(5).value(), id(6).value(), 0))
        .isInstanceOf(ConcurrentUpdateException.class);
    verifyNoInteractions(participants, audit);
  }
}
