package com.ngockhanh.clinic.healthexamination.application.service;

import static com.ngockhanh.clinic.healthexamination.AgreementFixtures.*;
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

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantImportCommitter.Request;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantImportCommitter.Row;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.SessionAssignment;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
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
  private final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);
  private final HealthExaminationParticipantRepository participants =
      mock(HealthExaminationParticipantRepository.class);
  private final HealthExaminationSessionRepository sessions =
      mock(HealthExaminationSessionRepository.class);
  private final SessionAssignmentRepository assignments = mock(SessionAssignmentRepository.class);
  private final ParticipantImportStore imports = mock(ParticipantImportStore.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-10-06T01:02:03.456789Z"), ZoneOffset.UTC);
  private final ParticipantImportCommitter committer =
      new ParticipantImportCommitter(
          organizations, agreements, participants, sessions, assignments, imports, audit, clock);

  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private final UUID key = UUID.randomUUID();
  private final UUID reservationId = UUID.randomUUID();
  private final UUID jobId = UUID.randomUUID();

  @BeforeEach
  void anActiveOrganizationAndADraftAgreementAtVersionTwo() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(
            Optional.of(
                details(draftAgreement(organizationId, agreementId, 2, UUID.randomUUID()))));
    when(imports.reserve(any())).thenReturn(new ImportReservation.New(reservationId));
    when(imports.createValidatedJob(any())).thenReturn(jobId);
    when(participants.findExistingIdentities(any(), anyList())).thenReturn(List.of());
  }

  private static Row row(int number, String identification) {
    return new Row(
        number,
        new Roster(
            null,
            "Synthetic Person " + number,
            LocalDate.of(1990, 1, 31),
            "MALE",
            new IdentificationNumber(identification),
            null,
            null,
            "Department",
            "Position"));
  }

  private Request request(long version, List<Row> rows) {
    return new Request(organizationId, agreementId, version, key, new byte[32], rows, actorId);
  }

  @Test
  void importsInTheDocumentedOrderAndWritesOneAuditEvent() {
    var outcome = committer.commit(request(2, List.of(row(2, "111"), row(3, "222"))));

    assertThat(outcome.replayed()).isFalse();
    assertThat(outcome.receipt().importJobId()).isEqualTo(jobId);
    assertThat(outcome.receipt().totalRows()).isEqualTo(2);
    assertThat(outcome.receipt().createdCount()).isEqualTo(2);
    assertThat(outcome.receipt().completedAt())
        .isEqualTo(Instant.parse("2026-10-06T01:02:03.456Z"));

    var order = inOrder(agreements, imports, participants, audit);
    order.verify(agreements).findDetails(organizationId, agreementId, true);
    order.verify(imports).reserve(any());
    order.verify(participants).findExistingIdentities(any(), anyList());
    order.verify(imports).createValidatedJob(any());
    order.verify(participants).insertMany(anyList());
    order.verify(imports).markRowsCommitted(eq(jobId), anyList());
    order.verify(imports).confirmJob(eq(jobId), eq(0L), any(ImportReceipt.class));
    order.verify(imports).completeRequest(eq(reservationId), any(ImportReceipt.class));
    order
        .verify(audit)
        .record(
            eq(actorId),
            eq("IMPORT_PARTICIPANTS"),
            eq("HEALTH_EXAMINATION_AGREEMENT"),
            eq(agreementId),
            eq(null),
            any());
  }

  @Test
  void linksRowsToTheImportJobAndTheirOrganizationAndLeavesThemUnscheduled() {
    committer.commit(request(2, List.of(row(2, "111"))));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<HealthExaminationParticipant>> inserted =
        ArgumentCaptor.forClass(List.class);
    verify(participants).insertMany(inserted.capture());
    var created = inserted.getValue().get(0);
    assertThat(property(created, "importJobId")).isEqualTo(AggregateId.of(jobId));
    assertThat(property(created, "sourceRowNumber")).isEqualTo(2);
    assertThat(property(created, "agreementId")).isEqualTo(AggregateId.of(agreementId));
    assertThat(property(created, "organizationId")).isEqualTo(AggregateId.of(organizationId));
  }

  /**
   * Reads a property whether the aggregate exposes fluent ({@code x()}) or bean ({@code getX()})
   * accessors.
   */
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
    for (int i = 0; i < 450; i++) rows.add(row(i + 2, String.valueOf(1_000_000 + i)));

    var outcome = committer.commit(request(2, rows));

    assertThat(outcome.receipt().createdCount()).isEqualTo(450);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<HealthExaminationParticipant>> chunks = ArgumentCaptor.forClass(List.class);
    verify(participants, times(3)).insertMany(chunks.capture());
    assertThat(chunks.getAllValues()).extracting(List::size).containsExactly(200, 200, 50);
  }

  @Test
  void aCompletedIdenticalRequestReplaysWithoutTouchingTheAgreementVersionOrData() {
    var stored = new ImportReceipt(jobId, agreementId, 2, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ImportReservation.Replay(stored));

    // the agreement has moved on (version 2) but the replay must still succeed with the old version
    var outcome = committer.commit(request(1, List.of(row(2, "111"))));

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.receipt()).isEqualTo(stored);
    verifyNoInteractions(participants, audit);
    verify(imports, never()).createValidatedJob(any());
  }

  @Test
  void aStaleVersionIsRejectedBeforeAnyWrite() {
    assertThatThrownBy(() -> committer.commit(request(1, List.of(row(2, "111")))))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(imports, never()).createValidatedJob(any());
    verify(participants, never()).insertMany(anyList());
    verifyNoInteractions(audit);
  }

  @Test
  void anIdentityAlreadyInTheAgreementIsRejectedWithoutEchoingIt() {
    when(participants.findExistingIdentities(any(), anyList()))
        .thenReturn(List.of(new IdentificationNumber("222")));

    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(2, "111"), row(3, "222")))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant identity already exists in this agreement at row 3");
    verify(imports, never()).createValidatedJob(any());
    verify(participants, never()).insertMany(anyList());
    verifyNoInteractions(audit);
  }

  @Test
  void inactiveOrganizationAndEndedAgreementAreConflicts() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(2, "111")))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Agreement does not accept Participant imports");

    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(
            Optional.of(
                details(
                    agreement(
                        organizationId,
                        agreementId,
                        AgreementStatus.ENDED,
                        2,
                        null,
                        UUID.randomUUID()))));
    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(2, "111")))))
        .isInstanceOf(ConflictException.class);
    verify(imports, never()).createValidatedJob(any());
    verify(imports, never()).completeRequest(any(), any());
    verify(participants, never()).insertMany(anyList());
    verifyNoInteractions(audit);
  }

  @Test
  void aCompletedIdenticalRequestStillReplaysAfterTheOrganizationIsDeactivated() {
    var stored = new ImportReceipt(jobId, agreementId, 2, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ImportReservation.Replay(stored));
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));

    var outcome = committer.commit(request(2, List.of(row(2, "111"))));

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.receipt()).isEqualTo(stored);
    verifyNoInteractions(participants, audit);
  }

  @Test
  void aCompletedIdenticalRequestStillReplaysAfterTheAgreementEnded() {
    var stored = new ImportReceipt(jobId, agreementId, 2, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ImportReservation.Replay(stored));
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(
            Optional.of(
                details(
                    agreement(
                        organizationId,
                        agreementId,
                        AgreementStatus.ENDED,
                        3,
                        null,
                        UUID.randomUUID()))));

    var outcome = committer.commit(request(2, List.of(row(2, "111"))));

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.receipt()).isEqualTo(stored);
    verifyNoInteractions(participants, audit);
  }

  private final UUID sessionId = UUID.randomUUID();

  private Request requestWithSession(List<Row> rows) {
    return new Request(
        organizationId, agreementId, 2, key, new byte[32], rows, actorId, sessionId);
  }

  private HealthExaminationSession session(UUID servedAgreement, SessionStatus status) {
    return HealthExaminationSession.restore(
        AggregateId.of(sessionId),
        AggregateId.of(organizationId),
        servedAgreement == null ? null : AggregateId.of(servedAgreement),
        LocalDate.of(2026, 10, 20),
        SessionActivity.ALL,
        new ExaminationSite(ExaminationSiteType.CLINIC, null),
        status,
        0);
  }

  private void givenSession(HealthExaminationSession session) {
    when(sessions.findById(organizationId, sessionId, true)).thenReturn(Optional.of(session));
  }

  @Test
  void aNamedSessionReceivesEveryImportedParticipantInTheSameTransaction() {
    givenSession(session(agreementId, SessionStatus.OPEN));

    var outcome = committer.commit(requestWithSession(List.of(row(2, "111"), row(3, "222"))));

    assertThat(outcome.replayed()).isFalse();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<HealthExaminationParticipant>> insertedParticipants =
        ArgumentCaptor.forClass(List.class);
    verify(participants).insertMany(insertedParticipants.capture());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SessionAssignment>> insertedAssignments = ArgumentCaptor.forClass(List.class);
    verify(assignments).insertMany(insertedAssignments.capture());
    assertThat(insertedAssignments.getValue())
        .extracting(SessionAssignment::participantId)
        .containsExactlyElementsOf(
            insertedParticipants.getValue().stream()
                .map(HealthExaminationParticipant::getId)
                .toList());
    assertThat(insertedAssignments.getValue())
        .allSatisfy(a -> assertThat(a.sessionId()).isEqualTo(AggregateId.of(sessionId)));

    var order = inOrder(sessions, participants, assignments, audit);
    order.verify(sessions).findById(organizationId, sessionId, true);
    order.verify(participants).insertMany(anyList());
    order.verify(assignments).insertMany(anyList());
    order
        .verify(audit)
        .record(
            eq(actorId),
            eq("ASSIGN_PARTICIPANTS_TO_SESSION"),
            eq("HEALTH_EXAMINATION_SESSION"),
            eq(sessionId),
            eq(null),
            any());
  }

  @Test
  void aSharedSessionOfTheOrganizationTakesTheImportedParticipantsToo() {
    givenSession(session(null, SessionStatus.OPEN));

    committer.commit(requestWithSession(List.of(row(2, "111"))));

    verify(assignments).insertMany(anyList());
  }

  @Test
  void importWithoutASessionSchedulesNobody() {
    committer.commit(request(2, List.of(row(2, "111"))));

    verifyNoInteractions(sessions, assignments);
  }

  @Test
  void aMissingSessionRejectsTheWholeImportBeforeAnyWrite() {
    when(sessions.findById(organizationId, sessionId, true)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> committer.commit(requestWithSession(List.of(row(2, "111")))))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(imports, never()).createValidatedJob(any());
    verify(participants, never()).insertMany(anyList());
    verifyNoInteractions(assignments, audit);
  }

  @Test
  void aCancelledSessionRejectsTheWholeImportBeforeAnyWrite() {
    givenSession(session(agreementId, SessionStatus.CANCELLED));

    assertThatThrownBy(() -> committer.commit(requestWithSession(List.of(row(2, "111")))))
        .isInstanceOf(DomainRuleViolation.class);
    verify(imports, never()).createValidatedJob(any());
    verify(participants, never()).insertMany(anyList());
    verifyNoInteractions(assignments, audit);
  }

  @Test
  void aSessionOfAnotherAgreementRejectsTheWholeImportBeforeAnyWrite() {
    givenSession(session(UUID.randomUUID(), SessionStatus.OPEN));

    assertThatThrownBy(() -> committer.commit(requestWithSession(List.of(row(2, "111")))))
        .isInstanceOf(DomainRuleViolation.class);
    verify(imports, never()).createValidatedJob(any());
    verify(participants, never()).insertMany(anyList());
    verifyNoInteractions(assignments, audit);
  }

  @Test
  void insertsAssignmentsInChunksOf200() {
    givenSession(session(agreementId, SessionStatus.OPEN));
    List<Row> rows = new ArrayList<>();
    for (int i = 0; i < 450; i++) rows.add(row(i + 2, String.valueOf(1_000_000 + i)));

    committer.commit(requestWithSession(rows));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SessionAssignment>> chunks = ArgumentCaptor.forClass(List.class);
    verify(assignments, times(3)).insertMany(chunks.capture());
    assertThat(chunks.getAllValues()).extracting(List::size).containsExactly(200, 200, 50);
  }

  @Test
  void aReplayDoesNotLookAtTheSessionAgain() {
    var stored = new ImportReceipt(jobId, agreementId, 2, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ImportReservation.Replay(stored));

    var outcome = committer.commit(requestWithSession(List.of(row(2, "111"))));

    assertThat(outcome.replayed()).isTrue();
    verifyNoInteractions(sessions, assignments);
  }

  @Test
  void aMissingAgreementOrOrganizationIsNotFound() {
    when(agreements.findDetails(organizationId, agreementId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(2, "111")))))
        .isInstanceOf(ResourceNotFoundException.class);

    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(
            Optional.of(
                details(draftAgreement(organizationId, agreementId, 2, UUID.randomUUID()))));
    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> committer.commit(request(2, List.of(row(2, "111")))))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(imports);
  }
}
