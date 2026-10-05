package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantDayAllocator;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class StoreValidatedParticipantImportUseCaseTest {
  final HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
  final HealthExaminationImportJobRepository jobs =
      mock(HealthExaminationImportJobRepository.class);
  final HealthExaminationBatchParticipantRepository participants =
      mock(HealthExaminationBatchParticipantRepository.class);
  final ParticipantImportAuditWriter audit = mock(ParticipantImportAuditWriter.class);
  final StoreValidatedParticipantImportUseCase usecase =
      new StoreValidatedParticipantImportUseCase(
          CLOCK, batches, jobs, participants, audit, new ParticipantDayAllocator());

  @BeforeEach
  void setup() {
    when(batches.findByIdAndOrganizationIdForUpdate(id(1), id(2))).thenReturn(Optional.of(batch()));
  }

  @Test
  void invalidFileCreatesNoJobOrStaging() {
    var row = row(1);
    row.reject("MISSING_POSITION_NAME");
    var result =
        usecase.execute(
            id(2).value(), id(1).value(), id(6).value(), List.of(id(3).value()), List.of(row));
    assertThat(result.importId()).isNull();
    assertThat(result.rows().getFirst().errors()).contains("MISSING_POSITION_NAME");
    verifyNoInteractions(jobs, audit);
  }

  @Test
  void organizationBatchAndBatchDayScopeAreCheckedBeforeStaging() {
    when(batches.findByIdAndOrganizationIdForUpdate(id(1), id(9))).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                usecase.execute(
                    id(9).value(),
                    id(1).value(),
                    id(6).value(),
                    List.of(id(3).value()),
                    List.of(row(1))))
        .isInstanceOf(com.ngockhanh.clinic.shared.exception.ResourceNotFoundException.class);

    assertThatThrownBy(
            () ->
                usecase.execute(
                    id(2).value(),
                    id(1).value(),
                    id(6).value(),
                    List.of(id(7).value()),
                    List.of(row(1))))
        .isInstanceOf(com.ngockhanh.clinic.shared.exception.BusinessRuleException.class);

    verifyNoInteractions(jobs, audit);
  }

  @Test
  void duplicatesWithinFileAndExistingBatchRejectWholeUpload() {
    var a = row(1);
    var b =
        new HealthExaminationImportRow(
            id(200),
            2,
            null,
            "Other",
            a.getDateOfBirth(),
            a.getSex(),
            a.getIdentificationNumber(),
            null,
            null,
            "Department",
            "Position",
            List.of());
    when(participants.existingIdentificationNumbers(any(), any()))
        .thenReturn(Set.of(a.getIdentificationNumber()));
    var result =
        usecase.execute(
            id(2).value(), id(1).value(), id(6).value(), List.of(id(3).value()), List.of(a, b));
    assertThat(result.importId()).isNull();
    assertThat(a.getErrorCodes()).contains("DUPLICATE_IN_FILE", "DUPLICATE_IN_BATCH");
    verifyNoInteractions(jobs, audit);
  }

  @Test
  void allocationAddsOnlyNewPeopleToLeastPopulatedDays() {
    var days =
        List.of(
            new HealthExaminationBatchDay(id(3).value(), LocalDate.of(2026, 10, 4)),
            new HealthExaminationBatchDay(id(4).value(), LocalDate.of(2026, 10, 5)),
            new HealthExaminationBatchDay(id(7).value(), LocalDate.of(2026, 10, 6)));
    var rows = java.util.stream.IntStream.rangeClosed(1, 30).mapToObj(n -> row(n)).toList();
    new ParticipantDayAllocator()
        .assignDays(rows, days, Map.of(id(3).value(), 60L, id(4).value(), 40L, id(7).value(), 40L));
    assertThat(rows.stream().filter(r -> r.getBatchDayId().equals(id(3))).count()).isZero();
    assertThat(rows.stream().filter(r -> r.getBatchDayId().equals(id(4))).count()).isEqualTo(15);
    assertThat(rows.stream().filter(r -> r.getBatchDayId().equals(id(7))).count()).isEqualTo(15);
  }

  @Test
  void tieBreaksByDateThenUuidAndKeepsSelectedSetFrozen() {
    var rows = List.of(row(1), row(2));
    var days =
        List.of(
            new HealthExaminationBatchDay(id(4).value(), LocalDate.of(2026, 10, 4)),
            new HealthExaminationBatchDay(id(3).value(), LocalDate.of(2026, 10, 4)));
    new ParticipantDayAllocator().assignDays(rows, days, Map.of());
    assertThat(rows.getFirst().getBatchDayId()).isEqualTo(id(3));
    assertThat(rows.getLast().getBatchDayId()).isEqualTo(id(4));
  }
}
