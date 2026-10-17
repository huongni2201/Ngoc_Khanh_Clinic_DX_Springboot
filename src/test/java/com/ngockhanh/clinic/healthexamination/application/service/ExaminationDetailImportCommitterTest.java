package com.ngockhanh.clinic.healthexamination.application.service;

import static com.ngockhanh.clinic.healthexamination.AgreementFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailImportCommitter.Request;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationImportStore;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReceipt;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReservation;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExaminationDetailImportCommitterTest {
  private static final Instant EARLIER = Instant.parse("2026-10-04T01:00:00Z");

  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);
  private final HealthExaminationParticipantRepository participants =
      mock(HealthExaminationParticipantRepository.class);
  private final ServiceReconciliationImportStore imports =
      mock(ServiceReconciliationImportStore.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-10-06T01:02:03.456789Z"), ZoneOffset.UTC);
  private final ExaminationDetailImportCommitter committer =
      new ExaminationDetailImportCommitter(
          organizations, agreements, participants, imports, audit, clock);

  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private final UUID key = UUID.randomUUID();
  private final UUID reservationId = UUID.randomUUID();
  private final UUID jobId = UUID.randomUUID();
  private HealthExaminationAgreement agreement;
  private UUID serviceA;
  private UUID serviceB;

  @BeforeEach
  void anActiveOrganizationAndADraftAgreementWithTwoServices() {
    givenAgreement(AgreementStatus.DRAFT, true);
    when(imports.reserve(any()))
        .thenReturn(new ServiceReconciliationReservation.New(reservationId));
    when(imports.createValidatedJob(any())).thenReturn(jobId);
  }

  private void givenAgreement(AgreementStatus status, boolean activeOrganization) {
    agreement =
        agreement(
            organizationId, agreementId, status, 2, null, UUID.randomUUID(), UUID.randomUUID());
    serviceA = agreement.services().get(0).id().value();
    serviceB = agreement.services().get(1).id().value();
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(Optional.of(details(agreement)));
    var organization =
        activeOrganization ? organization(organizationId) : inactiveOrganization(organizationId);
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization));
  }

  private HealthExaminationParticipant participant(RosterStatus roster, long version) {
    return HealthExaminationParticipant.restore(
        AggregateId.of(UUID.randomUUID()),
        AggregateId.of(organizationId),
        AggregateId.of(agreementId),
        new Roster(
            "NV",
            "Synthetic Person",
            LocalDate.of(1990, 1, 1),
            "MALE",
            IdentificationNumber.of(
                String.valueOf(100000000000L + version + (long) (Math.random() * 1e9))),
            null,
            null,
            "Department",
            "Position"),
        new Progress(null, roster, ReconciliationStatus.PENDING, null, null, null),
        null,
        null,
        EARLIER,
        EARLIER,
        version,
        List.of());
  }

  private void givenLocked(HealthExaminationParticipant... locked) {
    when(participants.findManyInAgreementForUpdate(
            eq(AggregateId.of(agreementId)), anyCollection()))
        .thenReturn(List.of(locked));
  }

  private ExaminationDetailImportRow row(
      int number, HealthExaminationParticipant p, long version, UUID... ticked) {
    return new ExaminationDetailImportRow(number, p.getId().value(), version, Set.of(ticked));
  }

  private Request request(List<ExaminationDetailImportRow> rows) {
    return new Request(
        organizationId,
        agreementId,
        key,
        new byte[32],
        2,
        Set.of(serviceA, serviceB),
        rows,
        actorId);
  }

  @Test
  void reconcilesChangedRowsSavesOnlyThemAndWritesOneAuditEvent() {
    var changing = participant(RosterStatus.ACTIVE, 4);
    var nothing = participant(RosterStatus.ACTIVE, 5);
    givenLocked(changing, nothing);

    var outcome =
        committer.commit(
            request(List.of(row(3, changing, 4, serviceA, serviceB), row(4, nothing, 5))));

    assertThat(outcome.replayed()).isFalse();
    var receipt = outcome.receipt();
    assertThat(receipt.importJobId()).isEqualTo(jobId);
    assertThat(receipt.totalRows()).isEqualTo(2);
    assertThat(receipt.updatedParticipants()).isEqualTo(1);
    assertThat(receipt.unchangedParticipants()).isEqualTo(1);
    assertThat(receipt.performedItems()).isEqualTo(2);
    assertThat(receipt.completedAt()).isEqualTo(Instant.parse("2026-10-06T01:02:03.456Z"));

    assertThat(changing.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    assertThat(changing.performedAgreementServiceIds()).hasSize(2);
    assertThat(nothing.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);

    var order = inOrder(agreements, imports, participants, audit);
    order.verify(agreements).findDetails(organizationId, agreementId, true);
    order.verify(imports).reserve(any());
    order.verify(participants).findManyInAgreementForUpdate(any(), anyCollection());
    order.verify(participants).save(changing, 4L);
    order.verify(imports).createValidatedJob(any());
    order.verify(imports).markRowsCommitted(eq(jobId), anyList());
    order.verify(imports).confirmJob(eq(jobId), eq(0L), any(ServiceReconciliationReceipt.class));
    order
        .verify(imports)
        .completeRequest(eq(reservationId), any(ServiceReconciliationReceipt.class));
    order
        .verify(audit)
        .record(
            eq(actorId),
            eq("IMPORT_EXAMINATION_DETAILS"),
            eq("HEALTH_EXAMINATION_AGREEMENT"),
            eq(agreementId),
            eq(null),
            any());
    verify(participants, never()).save(eq(nothing), anyLong());
  }

  @Test
  @SuppressWarnings("unchecked")
  void linksOnlyChangedRowsToTheJobAndKeepsPersonalDataOutOfTheAuditPayload() {
    var changing = participant(RosterStatus.ACTIVE, 4);
    var nothing = participant(RosterStatus.ACTIVE, 5);
    givenLocked(changing, nothing);

    committer.commit(request(List.of(row(3, changing, 4, serviceA), row(4, nothing, 5))));

    ArgumentCaptor<List<com.ngockhanh.clinic.integration.application.imports.CommittedImportRow>>
        committed = ArgumentCaptor.forClass(List.class);
    verify(imports).markRowsCommitted(eq(jobId), committed.capture());
    assertThat(committed.getValue()).hasSize(1);
    assertThat(committed.getValue().get(0).rowNumber()).isEqualTo(3);
    assertThat(committed.getValue().get(0).resourceId()).isEqualTo(changing.getId().value());

    ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
    verify(audit).record(any(), any(), any(), any(), any(), after.capture());
    assertThat(after.getValue().toString()).doesNotContain("Synthetic Person");
  }

  @Test
  void aReplayReturnsTheStoredReceiptWithoutTouchingAnyParticipantOrTheAudit() {
    var stored =
        new ServiceReconciliationReceipt(
            jobId, agreementId, 2, 1, 1, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ServiceReconciliationReservation.Replay(stored));

    var outcome = committer.commit(request(List.of()));

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.receipt()).isEqualTo(stored);
    verifyNoInteractions(participants, audit);
    verify(imports, never()).createValidatedJob(any());
  }

  @Test
  void aReplayStillWorksAfterTheOrganizationBecameInactive() {
    givenAgreement(AgreementStatus.DRAFT, false);
    var stored =
        new ServiceReconciliationReceipt(
            jobId, agreementId, 2, 1, 1, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ServiceReconciliationReservation.Replay(stored));

    assertThat(committer.commit(request(List.of())).replayed()).isTrue();
  }

  @Test
  void rejectsAFileThatDoesNotDeclareExactlyTheServicesOfTheAgreement() {
    var p = participant(RosterStatus.ACTIVE, 1);
    var other =
        new Request(
            organizationId,
            agreementId,
            key,
            new byte[32],
            2,
            Set.of(serviceA),
            List.of(row(3, p, 1)),
            actorId);

    assertThatThrownBy(() -> committer.commit(other))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT))
        .hasMessage("The file does not match this agreement; export it again");
    verifyNoInteractions(participants, audit);
    verify(imports, never()).createValidatedJob(any());
  }

  @Test
  void aStaleRowVersionRejectsTheWholeFileAndSavesNothing() {
    var fine = participant(RosterStatus.ACTIVE, 1);
    var stale = participant(RosterStatus.ACTIVE, 7);
    givenLocked(fine, stale);

    assertThatThrownBy(
            () ->
                committer.commit(
                    request(List.of(row(3, fine, 1, serviceA), row(4, stale, 6, serviceA)))))
        .isInstanceOf(ConcurrentUpdateException.class)
        .hasMessageContaining("Row 4");
    verify(participants, never()).save(any(), anyLong());
    verify(imports, never()).createValidatedJob(any());
    verifyNoInteractions(audit);
    assertThat(fine.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
  }

  @Test
  void aParticipantOfAnotherAgreementOrACancelledOneIsAConflict() {
    var cancelled = participant(RosterStatus.CANCELLED, 1);
    var foreign = participant(RosterStatus.ACTIVE, 1);
    givenLocked(cancelled);

    assertThatThrownBy(() -> committer.commit(request(List.of(row(3, cancelled, 1, serviceA)))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Row 3: participant is cancelled");
    assertThatThrownBy(() -> committer.commit(request(List.of(row(4, foreign, 1, serviceA)))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Row 4: participant is not in this agreement");
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void anEndedAgreementOrAnInactiveOrganizationIsAConflict() {
    var p = participant(RosterStatus.ACTIVE, 1);
    givenAgreement(AgreementStatus.ENDED, true);
    assertThatThrownBy(() -> committer.commit(request(List.of(row(3, p, 1, serviceA)))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Agreement does not accept examination detail changes");
    givenAgreement(AgreementStatus.DRAFT, false);
    assertThatThrownBy(() -> committer.commit(request(List.of(row(3, p, 1, serviceA)))))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(participants, audit);
  }

  @Test
  void anActiveAgreementAcceptsTheImport() {
    givenAgreement(AgreementStatus.ACTIVE, true);
    var p = participant(RosterStatus.ACTIVE, 1);
    givenLocked(p);

    assertThat(
            committer
                .commit(request(List.of(row(3, p, 1, serviceA))))
                .receipt()
                .updatedParticipants())
        .isEqualTo(1);
  }

  @Test
  void anUnknownOrganizationOrAgreementIsNotFound() {
    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> committer.commit(request(List.of())))
        .isInstanceOf(ResourceNotFoundException.class);

    when(agreements.findDetails(organizationId, agreementId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> committer.commit(request(List.of())))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(participants, audit);
  }
}
