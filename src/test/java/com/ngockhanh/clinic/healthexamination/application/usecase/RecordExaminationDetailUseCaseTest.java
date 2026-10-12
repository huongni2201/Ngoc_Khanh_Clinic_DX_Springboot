package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
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
import com.ngockhanh.clinic.healthexamination.application.command.RecordExaminationDetailCommand.Attendance;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
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

/** Manual entry of the examination details of one Participant: the rules of the PUT endpoint. */
class RecordExaminationDetailUseCaseTest {
  private static final String RECORD = ExaminationDetailAccessPolicy.SERVICE_RECORD_PERMISSION;
  private static final Instant EARLIER = Instant.parse("2026-10-04T01:00:00Z");
  // 08:00 on 2026-10-06 in Asia/Ho_Chi_Minh.
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-06T01:00:00.123456Z"), ZoneOffset.UTC);
  private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 5);

  private final ExaminationDetailAccessPolicy access = new ExaminationDetailAccessPolicy();
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final HealthExaminationBatchParticipantRepository participants =
      mock(HealthExaminationBatchParticipantRepository.class);
  private final ExaminationDetailReader reader = mock(ExaminationDetailReader.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final RecordExaminationDetailUseCase useCase =
      new RecordExaminationDetailUseCase(
          access, organizations, batches, participants, reader, audit, CLOCK);

  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID participantId = UUID.randomUUID();
  private final UserPrincipal principal = ParticipantFixtures.staff(RECORD);
  private HealthExaminationBatch batch;
  private UUID serviceA;
  private UUID serviceB;

  @BeforeEach
  void anActiveOrganizationAndADraftBatchOfTwoServices() {
    givenBatch(BatchStatus.DRAFT, true);
    when(reader.readOne(organizationId, batchId, participantId))
        .thenReturn(Optional.of(storedRow()));
  }

  private void givenBatch(BatchStatus status, boolean activeOrganization) {
    batch = batch(organizationId, batchId, status, 2, null, UUID.randomUUID(), UUID.randomUUID());
    serviceA = batch.services().get(0).id().value();
    serviceB = batch.services().get(1).id().value();
    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.of(details(batch)));
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
        YESTERDAY,
        "RECONCILED",
        List.of(),
        4);
  }

  private HealthExaminationBatchParticipantService stored(UUID service, boolean performed) {
    return new HealthExaminationBatchParticipantService(
        AggregateId.of(UUID.randomUUID()),
        AggregateId.of(batchId),
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

  private HealthExaminationBatchParticipant participant(
      RosterStatus roster,
      AttendanceStatus attendance,
      LocalDate actualDate,
      ReconciliationStatus reconciliation,
      long version,
      List<HealthExaminationBatchParticipantService> services) {
    boolean attended = attendance != AttendanceStatus.UNCONFIRMED;
    boolean reconciled = reconciliation == ReconciliationStatus.RECONCILED;
    AggregateId earlier = AggregateId.of(UUID.randomUUID());
    return HealthExaminationBatchParticipant.restore(
        AggregateId.of(participantId),
        AggregateId.of(batchId),
        AggregateId.of(batch.days().get(0).id()),
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
            attendance,
            actualDate,
            attended ? earlier : null,
            attended ? EARLIER : null,
            null,
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

  private HealthExaminationBatchParticipant unconfirmed(long version) {
    return participant(
        RosterStatus.ACTIVE,
        AttendanceStatus.UNCONFIRMED,
        null,
        ReconciliationStatus.PENDING,
        version,
        List.of());
  }

  private HealthExaminationBatchParticipant reconciledWith(
      long version, HealthExaminationBatchParticipantService... services) {
    return participant(
        RosterStatus.ACTIVE,
        AttendanceStatus.ATTENDED,
        YESTERDAY,
        ReconciliationStatus.RECONCILED,
        version,
        List.of(services));
  }

  private void givenParticipant(HealthExaminationBatchParticipant participant) {
    when(participants.findManyInBatchForUpdate(any(), any())).thenReturn(List.of(participant));
  }

  private RecordExaminationDetailCommand attended(LocalDate date, long version, UUID... services) {
    return new RecordExaminationDetailCommand(Attendance.ATTENDED, date, Set.of(services), version);
  }

  private RecordExaminationDetailCommand absent(long version) {
    return new RecordExaminationDetailCommand(Attendance.ABSENT, null, Set.of(), version);
  }

  private void run(RecordExaminationDetailCommand command) {
    useCase.execute(organizationId, batchId, participantId, command, principal);
  }

  // --- attended ---

  @Test
  void attendedMarksTheParticipantReconciledSavesAtTheExpectedVersionAndAuditsCounts() {
    var p = unconfirmed(3);
    givenParticipant(p);

    var response =
        useCase.execute(
            organizationId,
            batchId,
            participantId,
            attended(YESTERDAY, 3, serviceA, serviceB),
            principal);

    assertThat(response.rowVersion()).isEqualTo(4);
    assertThat(response.identificationNumberMasked()).doesNotContain("012345678901");
    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    assertThat(p.getActualExaminationDate()).isEqualTo(YESTERDAY);
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    assertThat(p.performedBatchServiceIds())
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
            eq("HEALTH_EXAMINATION_BATCH_PARTICIPANT"),
            eq(participantId),
            eq(null),
            details.capture());
    assertThat(details.getValue())
        .containsEntry("participantId", participantId)
        .containsEntry("attendanceStatus", "ATTENDED")
        .containsEntry("performedItems", 2)
        .doesNotContainKeys("fullName", "identificationNumber");
  }

  @Test
  void anUntickedServiceKeepsItsRowAsNotPerformed() {
    var p = reconciledWith(3, stored(serviceA, true), stored(serviceB, true));
    givenParticipant(p);

    run(attended(YESTERDAY, 3, serviceA));

    assertThat(p.performedBatchServiceIds()).containsExactly(AggregateId.of(serviceA));
    assertThat(p.services()).hasSize(2);
    verify(participants).save(p, 3);
  }

  @Test
  void aChangedActualDateIsRecordedOnAnAlreadyAttendedParticipant() {
    var p = reconciledWith(3, stored(serviceA, true));
    givenParticipant(p);

    run(attended(LocalDate.of(2026, 10, 4), 3, serviceA));

    assertThat(p.getActualExaminationDate()).isEqualTo(LocalDate.of(2026, 10, 4));
    verify(participants).save(p, 3);
  }

  @Test
  void aRequestEqualToTheStoredStateWritesNothing() {
    var p = reconciledWith(3, stored(serviceA, true));
    givenParticipant(p);

    run(attended(YESTERDAY, 3, serviceA));

    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void aNewlyTickedServiceThatIsNoLongerOfferedIsAConflict() {
    var inactive =
        new HealthExaminationBatchService(
            AggregateId.of(UUID.randomUUID()),
            AggregateId.of(UUID.randomUUID()),
            AggregateId.of(batchId),
            Money.vnd("200"),
            Money.vnd("100"),
            1,
            false,
            0);
    batch =
        HealthExaminationBatch.restoreConfiguration(
            AggregateId.of(batchId),
            AggregateId.of(organizationId),
            "B1",
            "Campaign",
            new ExaminationSite(ExaminationSiteType.CLINIC, "Address"),
            List.of(new HealthExaminationBatchDay(UUID.randomUUID(), FIRST_DAY)),
            List.of(inactive),
            BatchStatus.DRAFT,
            0,
            null);
    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.of(details(batch)));
    givenParticipant(unconfirmed(3));

    assertThatThrownBy(() -> run(attended(YESTERDAY, 3, inactive.id().value())))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("no longer offered")
        .hasMessageStartingWith("Participant");
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void anActualDateBeforeTheBatchOrAfterTodayIsInvalidInput() {
    givenParticipant(unconfirmed(3));

    for (LocalDate bad : List.of(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 7)))
      assertThatThrownBy(() -> run(attended(bad, 3, serviceA)))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT));
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void todayInBusinessTimeIsAcceptedEvenWhenTheServerDayDiffers() {
    // 17:30 UTC on 2026-10-06 is already 2026-10-07 in Vietnam.
    var lateClock = Clock.fixed(Instant.parse("2026-10-06T17:30:00Z"), ZoneOffset.UTC);
    var late =
        new RecordExaminationDetailUseCase(
            access, organizations, batches, participants, reader, audit, lateClock);
    givenParticipant(unconfirmed(3));

    late.execute(
        organizationId,
        batchId,
        participantId,
        attended(LocalDate.of(2026, 10, 7), 3, serviceA),
        principal);

    verify(participants).save(any(), eq(3L));
  }

  @Test
  void aServiceOutsideTheBatchIsInvalidInputBeforeAnyLock() {
    assertThatThrownBy(() -> run(attended(YESTERDAY, 3, UUID.randomUUID())))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT));
    verifyNoInteractions(participants, audit);
  }

  // --- date and services must fit the attendance ---

  @Test
  void anEntryThatDoesNotFitTheAttendanceIsInvalidInputBeforeAnyLookup() {
    var service = Set.of(serviceA);
    for (var bad :
        List.of(
            // attended without a date
            new RecordExaminationDetailCommand(Attendance.ATTENDED, null, service, 3),
            // attended without a service
            new RecordExaminationDetailCommand(Attendance.ATTENDED, YESTERDAY, Set.of(), 3),
            // absent with a service
            new RecordExaminationDetailCommand(Attendance.ABSENT, null, service, 3),
            // absent with a date
            new RecordExaminationDetailCommand(Attendance.ABSENT, YESTERDAY, Set.of(), 3)))
      assertThatThrownBy(() -> run(bad))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT));
    verifyNoInteractions(batches, participants, audit);
  }

  @Test
  void aCommandWithoutAttendanceIsRejected() {
    assertThatThrownBy(
            () -> run(new RecordExaminationDetailCommand(null, null, Set.of(), 3)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(participants, audit);
  }

  // --- absent ---

  @Test
  void absentRecordsAttendanceWithoutTouchingReconciliation() {
    var p = unconfirmed(3);
    givenParticipant(p);

    run(absent(3));

    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
    assertThat(p.getActualExaminationDate()).isNull();
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
    assertThat(p.services()).isEmpty();
    verify(participants).saveWithoutServices(p, 3);
    verify(participants, never()).save(any(), anyLong());
    verify(audit)
        .record(
            eq(principal.userId()),
            eq("RECORD_EXAMINATION_DETAIL"),
            eq("HEALTH_EXAMINATION_BATCH_PARTICIPANT"),
            eq(participantId),
            eq(null),
            any());
  }

  @Test
  void absentIsRefusedWhileAServiceIsRecordedAsPerformed() {
    var p = reconciledWith(3, stored(serviceA, true));
    givenParticipant(p);

    assertThatThrownBy(() -> run(absent(3)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("performed services");
    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void absentIsAllowedWhenEveryRecordedServiceWasUnticked() {
    var p = reconciledWith(3, stored(serviceA, false));
    givenParticipant(p);

    run(absent(3));

    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
    // The unticked service row is neither read nor written, so its version stays.
    verify(participants).saveWithoutServices(p, 3);
    verify(participants, never()).save(any(), anyLong());
    assertThat(p.services()).hasSize(1);
  }

  @Test
  void absentOnAnAlreadyAbsentParticipantWritesNothing() {
    var p =
        participant(
            RosterStatus.ACTIVE,
            AttendanceStatus.ABSENT,
            null,
            ReconciliationStatus.PENDING,
            3,
            List.of());
    givenParticipant(p);

    run(absent(3));

    verify(participants, never()).save(any(), anyLong());
    verify(participants, never()).saveWithoutServices(any(), anyLong());
    verifyNoInteractions(audit);
  }

  // --- concurrency ---

  @Test
  void aStaleVersionIsAConflictWhenTheStateIsNotTheRequestedOne() {
    givenParticipant(unconfirmed(5));

    assertThatThrownBy(() -> run(attended(YESTERDAY, 3, serviceA)))
        .isInstanceOfSatisfying(
            ConcurrentUpdateException.class,
            e -> assertThat(e.errorCode()).isEqualTo("PARTICIPANT_CHANGED"));
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void aStaleVersionWhoseStateAlreadyIsTheRequestIsARetryAndReturnsTheStoredRow() {
    givenParticipant(reconciledWith(4, stored(serviceA, true)));

    var response = useCase.execute(
        organizationId, batchId, participantId, attended(YESTERDAY, 3, serviceA), principal);

    assertThat(response.rowVersion()).isEqualTo(4);
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void aStaleAbsentRetryReturnsTheStoredRow() {
    givenParticipant(
        participant(
            RosterStatus.ACTIVE,
            AttendanceStatus.ABSENT,
            null,
            ReconciliationStatus.PENDING,
            4,
            List.of()));

    run(absent(3));

    verify(participants, never()).save(any(), anyLong());
  }

  // --- scope and access ---

  @Test
  void aCancelledParticipantIsAConflict() {
    givenParticipant(
        participant(
            RosterStatus.CANCELLED,
            AttendanceStatus.UNCONFIRMED,
            null,
            ReconciliationStatus.PENDING,
            3,
            List.of()));

    assertThatThrownBy(() -> run(attended(YESTERDAY, 3, serviceA)))
        .isInstanceOf(ConflictException.class);
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void aParticipantOfAnotherBatchIsNotFound() {
    when(participants.findManyInBatchForUpdate(any(), any())).thenReturn(List.of());

    assertThatThrownBy(() -> run(attended(YESTERDAY, 3, serviceA)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void anUnknownBatchIsNotFoundAndLocksNothing() {
    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> run(absent(3))).isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(participants, audit);
  }

  @Test
  void aFinalizedBatchOrAnInactiveOrganizationIsAConflict() {
    givenParticipant(unconfirmed(3));
    givenBatch(BatchStatus.FINALIZED, true);
    assertThatThrownBy(() -> run(absent(3))).isInstanceOf(ConflictException.class);

    givenBatch(BatchStatus.DRAFT, false);
    assertThatThrownBy(() -> run(absent(3))).isInstanceOf(ConflictException.class);

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
              () -> useCase.execute(organizationId, batchId, participantId, absent(3), denied))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(batches, participants, audit);
  }
}
