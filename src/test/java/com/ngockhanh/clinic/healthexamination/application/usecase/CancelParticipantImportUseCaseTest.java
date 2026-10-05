package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.*;
import org.junit.jupiter.api.Test;

class CancelParticipantImportUseCaseTest {
  @Test
  void cancellationAuditsOnceAndRemainsIdempotent() {
    var batches = mock(HealthExaminationBatchRepository.class);
    var jobs = mock(HealthExaminationImportJobRepository.class);
    var audit = mock(ParticipantImportAuditWriter.class);
    var job = job(0);
    when(batches.findByIdAndOrganizationId(id(1), id(2))).thenReturn(Optional.of(batch()));
    when(jobs.findByIdAndBatchIdForUpdate(id(5), id(1))).thenReturn(Optional.of(job));
    var usecase = new CancelParticipantImportUseCase(CLOCK, batches, jobs, audit);
    var first = usecase.execute(id(2).value(), id(1).value(), id(5).value(), id(6).value(), 0);
    assertThat(first.status()).isEqualTo("CANCELLED");
    assertThat(job.cancelledAt()).isNotNull();
    assertThat(usecase.execute(id(2).value(), id(1).value(), id(5).value(), id(6).value(), 0))
        .isEqualTo(first);
    verify(jobs, times(1)).save(job);
    verify(audit, times(1)).record(any());
  }

  @Test
  void staleCancellationDoesNotChangeTheImportOrWriteAudit() {
    var batches = mock(HealthExaminationBatchRepository.class);
    var jobs = mock(HealthExaminationImportJobRepository.class);
    var audit = mock(ParticipantImportAuditWriter.class);
    var job = job(1);
    when(batches.findByIdAndOrganizationId(id(1), id(2))).thenReturn(Optional.of(batch()));
    when(jobs.findByIdAndBatchIdForUpdate(id(5), id(1))).thenReturn(Optional.of(job));
    var usecase = new CancelParticipantImportUseCase(CLOCK, batches, jobs, audit);

    assertThatThrownBy(
            () -> usecase.execute(id(2).value(), id(1).value(), id(5).value(), id(6).value(), 0))
        .isInstanceOf(ConcurrentUpdateException.class);

    assertThat(job.status())
        .isEqualTo(com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus.VALIDATED);
    verify(jobs, never()).save(any());
    verifyNoInteractions(audit);
  }
}
