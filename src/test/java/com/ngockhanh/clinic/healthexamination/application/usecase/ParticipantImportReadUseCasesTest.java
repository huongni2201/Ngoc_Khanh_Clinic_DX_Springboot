package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.*;
import org.junit.jupiter.api.Test;

class ParticipantImportReadUseCasesTest {
  @Test
  void summariesNeedNoRetainedSourceFileAndKeepSelectedDaysAndVersion() {
    var batches = mock(HealthExaminationBatchRepository.class);
    var jobs = mock(HealthExaminationImportJobRepository.class);
    when(batches.findByIdAndOrganizationId(id(1), id(2))).thenReturn(Optional.of(batch()));
    when(jobs.findSummaryByIdAndBatchId(id(5), id(1)))
        .thenReturn(
            Optional.of(
                new HealthExaminationImportJobRepository.Summary(
                    id(5), List.of(id(3)), ImportStatus.VALIDATED, null, 4)));
    when(jobs.countRowsByJobId(id(5))).thenReturn(1L);
    var result =
        new GetParticipantImportUseCase(CLOCK, batches, jobs)
            .execute(id(2).value(), id(1).value(), id(5).value());
    assertThat(result.selectedBatchDayIds()).containsExactly(id(3).value());
    assertThat(result.rowVersion()).isEqualTo(4);
    assertThat(result.confirmAllowed()).isTrue();
  }

  @Test
  void rowsRemainScopedToOrganizationAndMaskIdentificationNumber() {
    var batches = mock(HealthExaminationBatchRepository.class);
    var jobs = mock(HealthExaminationImportJobRepository.class);
    when(batches.findByIdAndOrganizationId(id(1), id(2))).thenReturn(Optional.of(batch()));
    var job = job(0);
    when(jobs.findSummaryByIdAndBatchId(id(5), id(1)))
        .thenReturn(
            Optional.of(
                new HealthExaminationImportJobRepository.Summary(
                    id(5), List.of(id(3)), ImportStatus.VALIDATED, null, 0)));
    when(jobs.countRowsByJobId(id(5))).thenReturn(1L);
    when(jobs.findRowsByJobId(id(5), 0, 50)).thenReturn(job.rows());
    var usecase = new ListParticipantImportRowsUseCase(batches, jobs);
    var result = usecase.execute(id(2).value(), id(1).value(), id(5).value(), 1, 50, null);
    assertThat(result.rows().getFirst().maskedIdentificationNumber())
        .endsWith("0001")
        .doesNotContain("000000000001");
    assertThatThrownBy(
            () -> usecase.execute(id(9).value(), id(1).value(), id(5).value(), 1, 50, null))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
