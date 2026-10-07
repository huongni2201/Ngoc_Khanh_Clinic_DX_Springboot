package com.ngockhanh.clinic.healthexamination.application.service;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantImportCommitter.Request;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantImportCommitter.Row;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.integration.application.imports.ImportReceipt;
import com.ngockhanh.clinic.integration.application.imports.ImportReservation;
import com.ngockhanh.clinic.integration.application.imports.ParticipantImportStore;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ParticipantImportCommitterTest {
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
  private final HealthExaminationBatchParticipantRepository participants =
      mock(HealthExaminationBatchParticipantRepository.class);
  private final ParticipantImportStore imports = mock(ParticipantImportStore.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-06T01:02:03.456789Z"), ZoneOffset.UTC);
  private final ParticipantImportCommitter committer =
      new ParticipantImportCommitter(organizations, batches, participants, imports, audit, clock);

  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private final UUID key = UUID.randomUUID();
  private final UUID reservationId = UUID.randomUUID();
  private final UUID jobId = UUID.randomUUID();

  @BeforeEach
  void anActiveOrganizationAndADraftBatchAtVersionTwo() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(batches.findDetails(organizationId, batchId, true))
        .thenReturn(Optional.of(details(draftBatch(organizationId, batchId, 2, UUID.randomUUID()))));
    when(imports.reserve(any())).thenReturn(new ImportReservation.New(reservationId));
    when(imports.createValidatedJob(any())).thenReturn(jobId);
    when(participants.findExistingIdentities(any(), anyList())).thenReturn(List.of());
  }

  private static Row row(int number, String identification, LocalDate day) {
    return new Row(
        number,
        new Roster(
            null, "Synthetic Person " + number, LocalDate.of(1990, 1, 31), "MALE",
            new IdentificationNumber(identification), null, null, "Department", "Position"),
        day);
  }

  private Request request(long version, List<Row> rows) {
    return new Request(organizationId, batchId, version, key, new byte[32], rows, actorId);
  }

  @Test
  void importsInTheDocumentedOrderAndWritesOneAuditEvent() {
    var outcome = committer.commit(request(2, List.of(row(2, "111", FIRST_DAY), row(3, "222", SECOND_DAY))));

    assertThat(outcome.replayed()).isFalse();
    assertThat(outcome.receipt().importJobId()).isEqualTo(jobId);
    assertThat(outcome.receipt().totalRows()).isEqualTo(2);
    assertThat(outcome.receipt().createdCount()).isEqualTo(2);
    assertThat(outcome.receipt().completedAt()).isEqualTo(Instant.parse("2026-10-06T01:02:03.456Z"));

    var order = inOrder(batches, imports, participants, audit);
    order.verify(batches).findDetails(organizationId, batchId, true);
    order.verify(imports).reserve(any());
    order.verify(participants).findExistingIdentities(any(), anyList());
    order.verify(imports).createValidatedJob(any());
    order.verify(participants).insertMany(anyList());
    order.verify(imports).markRowsCommitted(eq(jobId), anyList());
    order.verify(imports).confirmJob(eq(jobId), eq(0L), any(ImportReceipt.class));
    order.verify(imports).completeRequest(eq(reservationId), any(ImportReceipt.class));
    order.verify(audit)
        .record(eq(actorId), eq("IMPORT_BATCH_PARTICIPANTS"), eq("HEALTH_EXAMINATION_BATCH"), eq(batchId), eq(null), any());
  }

  @Test
  void assignsRowsToTheirDayAndLinksThemToTheImportJob() {
    committer.commit(request(2, List.of(row(2, "111", SECOND_DAY))));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<HealthExaminationBatchParticipant>> inserted = ArgumentCaptor.forClass(List.class);
    verify(participants).insertMany(inserted.capture());
    var created = inserted.getValue().get(0);
    assertThat(property(created, "importJobId")).isEqualTo(AggregateId.of(jobId));
    assertThat(property(created, "sourceRowNumber")).isEqualTo(2);
    assertThat(property(created, "batchId")).isEqualTo(AggregateId.of(batchId));
  }

  /** Reads a property whether the aggregate exposes fluent ({@code x()}) or bean ({@code getX()}) accessors. */
  private static Object property(Object target, String name) {
    String bean = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    for (String candidate : List.of(name, bean)) {
      try {
        return target.getClass().getMethod(candidate).invoke(target);
      } catch (NoSuchMethodException next) {
        // try the other accessor style
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
    throw new IllegalStateException("No accessor for " + name);
  }

  @Test
  void insertsInChunksOf200() {
    List<Row> rows = new ArrayList<>();
    for (int i = 0; i < 450; i++) rows.add(row(i + 2, String.valueOf(1_000_000 + i), FIRST_DAY));

    var outcome = committer.commit(request(2, rows));

    assertThat(outcome.receipt().createdCount()).isEqualTo(450);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<HealthExaminationBatchParticipant>> chunks = ArgumentCaptor.forClass(List.class);
    verify(participants, times(3)).insertMany(chunks.capture());
    assertThat(chunks.getAllValues()).extracting(List::size).containsExactly(200, 200, 50);
  }

  @Test
  void aCompletedIdenticalRequestReplaysWithoutTouchingTheBatchVersionOrData() {
    var stored = new ImportReceipt(jobId, batchId, 2, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ImportReservation.Replay(stored));

    // the batch has moved on (version 2) but the replay must still succeed with the old version
    var outcome = committer.commit(request(1, List.of(row(2, "111", FIRST_DAY))));

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.receipt()).isEqualTo(stored);
    verifyNoInteractions(participants, audit);
    verify(imports, never()).createValidatedJob(any());
  }

  @Test
  void aStaleVersionIsRejectedBeforeAnyWrite() {
    assertThatThrownBy(() -> committer.commit(request(1, List.of(row(2, "111", FIRST_DAY)))))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(imports, never()).createValidatedJob(any());
    verify(participants, never()).insertMany(anyList());
    verifyNoInteractions(audit);
  }

  @Test
  void anExaminationDateOutsideTheBatchIsRejectedWithItsRow() {
    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(7, "111", LocalDate.of(2026, 12, 1))))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Row 7: examination_date is not a day of this batch");
    verify(participants, never()).insertMany(anyList());
  }

  @Test
  void anIdentityAlreadyInTheBatchIsRejectedWithoutEchoingIt() {
    when(participants.findExistingIdentities(any(), anyList()))
        .thenReturn(List.of(new IdentificationNumber("222")));

    assertThatThrownBy(
            () -> committer.commit(request(2, List.of(row(2, "111", FIRST_DAY), row(3, "222", FIRST_DAY)))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant identity already exists in this batch at row 3");
    verify(imports, never()).createValidatedJob(any());
    verify(participants, never()).insertMany(anyList());
    verifyNoInteractions(audit);
  }

  @Test
  void inactiveOrganizationAndFinalizedBatchAreConflicts() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(2, "111", FIRST_DAY)))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Batch does not accept Participant imports");

    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(batches.findDetails(organizationId, batchId, true))
        .thenReturn(
            Optional.of(
                details(batch(organizationId, batchId, BatchStatus.FINALIZED, 2, null, UUID.randomUUID()))));
    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(2, "111", FIRST_DAY)))))
        .isInstanceOf(ConflictException.class);
    verify(imports, never()).createValidatedJob(any());
    verify(imports, never()).completeRequest(any(), any());
    verify(participants, never()).insertMany(anyList());
    verifyNoInteractions(audit);
  }

  @Test
  void aCompletedIdenticalRequestStillReplaysAfterTheOrganizationIsDeactivated() {
    var stored = new ImportReceipt(jobId, batchId, 2, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ImportReservation.Replay(stored));
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));

    var outcome = committer.commit(request(2, List.of(row(2, "111", FIRST_DAY))));

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.receipt()).isEqualTo(stored);
    verifyNoInteractions(participants, audit);
  }

  @Test
  void aCompletedIdenticalRequestStillReplaysAfterTheBatchIsFinalized() {
    var stored = new ImportReceipt(jobId, batchId, 2, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ImportReservation.Replay(stored));
    when(batches.findDetails(organizationId, batchId, true))
        .thenReturn(
            Optional.of(
                details(batch(organizationId, batchId, BatchStatus.FINALIZED, 3, null, UUID.randomUUID()))));

    var outcome = committer.commit(request(2, List.of(row(2, "111", FIRST_DAY))));

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.receipt()).isEqualTo(stored);
    verifyNoInteractions(participants, audit);
  }

  @Test
  void aMissingBatchOrOrganizationIsNotFound() {
    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(2, "111", FIRST_DAY)))))
        .isInstanceOf(ResourceNotFoundException.class);

    when(batches.findDetails(organizationId, batchId, true))
        .thenReturn(Optional.of(details(draftBatch(organizationId, batchId, 2, UUID.randomUUID()))));
    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(2, "111", FIRST_DAY)))))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(imports);
  }
}
