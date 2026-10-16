package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.AgreementFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.application.command.RecordExaminationDetailCommand;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Manual entry of the services performed for one Participant: the rules of the PUT endpoint. */
class RecordExaminationDetailUseCaseTest {
  private static final String RECORD = ExaminationDetailAccessPolicy.SERVICE_RECORD_PERMISSION;
  private static final Instant EARLIER = Instant.parse("2026-10-04T01:00:00Z");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-06T01:00:00.123456Z"), ZoneOffset.UTC);

  private final ExaminationDetailAccessPolicy access = new ExaminationDetailAccessPolicy();
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);
  private final HealthExaminationParticipantRepository participants =
      mock(HealthExaminationParticipantRepository.class);
  private final ExaminationDetailReader reader = mock(ExaminationDetailReader.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final RecordExaminationDetailUseCase useCase =
      new RecordExaminationDetailUseCase(
          access, organizations, agreements, participants, reader, audit, CLOCK);

  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final UUID participantId = UUID.randomUUID();
  private final UserPrincipal principal = ParticipantFixtures.staff(RECORD);
  private HealthExaminationAgreement agreement;
  private UUID serviceA;
  private UUID serviceB;

  @BeforeEach
  void anActiveOrganizationAndADraftAgreementOfTwoServices() {
    givenAgreement(AgreementStatus.DRAFT, true);
    when(reader.readOne(organizationId, agreementId, participantId))
        .thenReturn(Optional.of(storedRow()));
  }

  private void givenAgreement(AgreementStatus status, boolean activeOrganization) {
    agreement =
        agreement(
            organizationId, agreementId, status, 2, null, UUID.randomUUID(), UUID.randomUUID());
    serviceA = agreement.services().get(0).id().value();
    serviceB = agreement.services().get(1).id().value();
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(Optional.of(details(agreement)));
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(
            Optional.of(
                activeOrganization
                    ? organization(organizationId)
                    : inactiveOrganization(organizationId)));
  }

  private static ExaminationDetailRow storedRow() {
    return new ExaminationDetailRow(
        UUID.randomUUID(),
        "NV-1",
        "Synthetic Person",
        LocalDate.of(1990, 1, 31),
        "MALE",
        "012345678901",
        "Accounting",
        "Staff",
        FIRST_DAY,
        "ATTENDED",
        FIRST_DAY,
        "RECONCILED",
        List.of(),
        4);
  }

  private HealthExaminationParticipantService stored(UUID service, boolean performed) {
    return new HealthExaminationParticipantService(
        AggregateId.of(UUID.randomUUID()),
        AggregateId.of(agreementId),
        AggregateId.of(participantId),
        AggregateId.of(service),
        performed,
        null,
        Money.vnd("100"),
        AggregateId.of(UUID.randomUUID()),
        EARLIER,
        EARLIER,
        EARLIER,
        0);
  }

  private HealthExaminationParticipant participant(
      RosterStatus roster,
      ReconciliationStatus reconciliation,
      long version,
      List<HealthExaminationParticipantService> services) {
    boolean reconciled = reconciliation == ReconciliationStatus.RECONCILED;
    AggregateId earlier = AggregateId.of(UUID.randomUUID());
    return HealthExaminationParticipant.restore(
        AggregateId.of(participantId),
        AggregateId.of(organizationId),
        AggregateId.of(agreementId),
        new Roster(
            "NV-1",
            "Synthetic Person",
            LocalDate.of(1990, 1, 1),
            "MALE",
            IdentificationNumber.of("000000000001"),
            null,
            null,
            "Department",
            "Position"),
        new Progress(
            null,
            roster,
            reconciliation,
            reconciled ? earlier : null,
            reconciled ? EARLIER : null,
            null),
        null,
        null,
        EARLIER,
        EARLIER,
        version,
        services);
  }

  private HealthExaminationParticipant pending(long version) {
    return participant(RosterStatus.ACTIVE, ReconciliationStatus.PENDING, version, List.of());
  }

  private HealthExaminationParticipant reconciledWith(
      long version, HealthExaminationParticipantService... services) {
    return participant(
        RosterStatus.ACTIVE, ReconciliationStatus.RECONCILED, version, List.of(services));
  }

  private void givenParticipant(HealthExaminationParticipant participant) {
    when(participants.findManyInAgreementForUpdate(any(), any())).thenReturn(List.of(participant));
  }

  private RecordExaminationDetailCommand performed(long version, UUID... services) {
    return new RecordExaminationDetailCommand(Set.of(services), version);
  }

  private void run(RecordExaminationDetailCommand command) {
    useCase.execute(organizationId, agreementId, participantId, command, principal);
  }

  @Test
  void recordingMarksTheParticipantReconciledSavesAtTheExpectedVersionAndAuditsCounts() {
    var p = pending(3);
    givenParticipant(p);

    var response =
        useCase.execute(
            organizationId,
            agreementId,
            participantId,
            performed(3, serviceA, serviceB),
            principal);

    assertThat(response.rowVersion()).isEqualTo(4);
    assertThat(response.identificationNumberMasked()).doesNotContain("012345678901");
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    assertThat(p.performedAgreementServiceIds())
        .containsExactlyInAnyOrder(AggregateId.of(serviceA), AggregateId.of(serviceB));
    assertThat(p.services())
        .allSatisfy(
            service ->
                assertThat(service.unitPriceSnapshot().amount()).isEqualByComparingTo("100"));
    verify(participants).save(p, 3);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
    verify(audit)
        .record(
            eq(principal.userId()),
            eq("RECORD_EXAMINATION_DETAIL"),
            eq("HEALTH_EXAMINATION_PARTICIPANT"),
            eq(participantId),
            eq(null),
            details.capture());
    assertThat(details.getValue())
        .containsEntry("participantId", participantId)
        .containsEntry("performedItems", 2)
        .doesNotContainKeys("fullName", "identificationNumber", "attendanceStatus");
  }

  @Test
  void anUntickedServiceKeepsItsRowAsNotPerformed() {
    var p = reconciledWith(3, stored(serviceA, true), stored(serviceB, true));
    givenParticipant(p);

    run(performed(3, serviceA));

    assertThat(p.performedAgreementServiceIds()).containsExactly(AggregateId.of(serviceA));
    assertThat(p.services()).hasSize(2);
    verify(participants).save(p, 3);
  }

  @Test
  void aRequestEqualToTheStoredStateWritesNothing() {
    var p = reconciledWith(3, stored(serviceA, true));
    givenParticipant(p);

    run(performed(3, serviceA));

    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void anEmptyEntryForAParticipantWithoutRowsWritesNothing() {
    var p = pending(3);
    givenParticipant(p);

    run(performed(3));

    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void aNewlyTickedServiceThatIsNoLongerOfferedIsAConflict() {
    var retired =
        new HealthExaminationAgreementService(
            AggregateId.of(UUID.randomUUID()),
            AggregateId.of(UUID.randomUUID()),
            AggregateId.of(agreementId),
            Money.vnd("200"),
            Money.vnd("100"),
            1,
            false,
            0);
    agreement =
        HealthExaminationAgreement.restoreConfiguration(
            AggregateId.of(agreementId),
            AggregateId.of(organizationId),
            "B1",
            "Campaign",
            FIRST_DAY,
            SECOND_DAY,
            List.of(retired),
            AgreementStatus.DRAFT,
            0,
            null);
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(Optional.of(details(agreement)));
    givenParticipant(pending(3));

    assertThatThrownBy(() -> run(performed(3, retired.id().value())))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("no longer offered")
        .hasMessageStartingWith("Participant");
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void aServiceOutsideTheAgreementIsInvalidInputBeforeAnyLock() {
    assertThatThrownBy(() -> run(performed(3, UUID.randomUUID())))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT));
    verifyNoInteractions(participants, audit);
  }

  @Test
  void aStaleVersionIsAConflictWhenTheStateIsNotTheRequestedOne() {
    givenParticipant(pending(5));

    assertThatThrownBy(() -> run(performed(3, serviceA)))
        .isInstanceOfSatisfying(
            ConcurrentUpdateException.class,
            e -> assertThat(e.errorCode()).isEqualTo("PARTICIPANT_CHANGED"));
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void aStaleVersionWhoseStateAlreadyIsTheRequestIsARetryAndReturnsTheStoredRow() {
    givenParticipant(reconciledWith(4, stored(serviceA, true)));

    var response =
        useCase.execute(
            organizationId, agreementId, participantId, performed(3, serviceA), principal);

    assertThat(response.rowVersion()).isEqualTo(4);
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void aCancelledParticipantIsAConflict() {
    givenParticipant(
        participant(RosterStatus.CANCELLED, ReconciliationStatus.PENDING, 3, List.of()));

    assertThatThrownBy(() -> run(performed(3, serviceA))).isInstanceOf(ConflictException.class);
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void aParticipantOfAnotherAgreementIsNotFound() {
    when(participants.findManyInAgreementForUpdate(any(), any())).thenReturn(List.of());

    assertThatThrownBy(() -> run(performed(3, serviceA)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void anUnknownAgreementIsNotFoundAndLocksNothing() {
    when(agreements.findDetails(organizationId, agreementId, true)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> run(performed(3))).isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(participants, audit);
  }

  @Test
  void anEndedAgreementOrAnInactiveOrganizationIsAConflict() {
    givenParticipant(pending(3));
    givenAgreement(AgreementStatus.ENDED, true);
    assertThatThrownBy(() -> run(performed(3, serviceA))).isInstanceOf(ConflictException.class);

    givenAgreement(AgreementStatus.DRAFT, false);
    assertThatThrownBy(() -> run(performed(3, serviceA))).isInstanceOf(ConflictException.class);

    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void withoutTheRecordPermissionNothingIsLookedUp() {
    for (UserPrincipal denied :
        List.of(
            ParticipantFixtures.staff(ExaminationDetailAccessPolicy.SERVICE_RECONCILE_PERMISSION),
            ParticipantFixtures.staff(),
            ParticipantFixtures.patient()))
      assertThatThrownBy(
              () ->
                  useCase.execute(organizationId, agreementId, participantId, performed(3), denied))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(agreements, participants, audit);
  }
}
