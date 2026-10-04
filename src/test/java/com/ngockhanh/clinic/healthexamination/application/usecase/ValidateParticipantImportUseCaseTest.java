package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.*;
import org.junit.jupiter.api.*;

class ValidateParticipantImportUseCaseTest {
  @Test
  void reorderedSelectionPreservesPreviouslyReviewedAssignment() {
    var job = job(0);
    job.reviseDays(List.of(id(3), id(4)), NOW);
    job.rows().getFirst().assignDay(id(4));
    when(jobs.findByIdAndBatchIdForUpdate(id(5), id(1))).thenReturn(Optional.of(job));

    usecase.execute(
        id(2).value(),
        id(1).value(),
        id(5).value(),
        id(6).value(),
        0,
        List.of(id(4).value(), id(3).value()),
        Map.of());

    assertThat(job.rows().getFirst().getBatchDayId()).isEqualTo(id(4));
    verify(participants, never()).activeCountsByDay(any());
  }

  final HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
  final HealthExaminationImportJobRepository jobs =
      mock(HealthExaminationImportJobRepository.class);
  final HealthExaminationBatchParticipantRepository participants =
      mock(HealthExaminationBatchParticipantRepository.class);
  final ParticipantImportAuditWriter audit = mock(ParticipantImportAuditWriter.class);
  final ValidateParticipantImportUseCase usecase =
      new ValidateParticipantImportUseCase(CLOCK, batches, jobs, participants, audit);

  @BeforeEach
  void setup() {
    when(batches.findByIdAndOrganizationIdForUpdate(id(1), id(2))).thenReturn(Optional.of(batch()));
  }

  @Test
  void rejectsStalePreviewBeforeAnyWrite() {
    when(jobs.findByIdAndBatchIdForUpdate(id(5), id(1))).thenReturn(Optional.of(job(1)));
    assertThatThrownBy(
            () ->
                usecase.execute(
                    id(2).value(),
                    id(1).value(),
                    id(5).value(),
                    id(6).value(),
                    0,
                    List.of(id(3).value()),
                    Map.of()))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(jobs, never()).save(any());
    verifyNoInteractions(audit);
  }

  @Test
  void preservesExplicitAssignmentsAndIncrementsApprovedVersion() {
    var job = job(0);
    when(jobs.findByIdAndBatchIdForUpdate(id(5), id(1))).thenReturn(Optional.of(job));
    doAnswer(
            i -> {
              job.markStored();
              return null;
            })
        .when(jobs)
        .save(job);
    var result =
        usecase.execute(
            id(2).value(),
            id(1).value(),
            id(5).value(),
            id(6).value(),
            0,
            List.of(id(3).value(), id(4).value()),
            Map.of(1, id(4).value()));
    assertThat(job.rows().getFirst().getBatchDayId()).isEqualTo(id(4));
    assertThat(result.rowVersion()).isEqualTo(1);
    verify(jobs).save(job);
    verify(audit).record(any());
  }
}
