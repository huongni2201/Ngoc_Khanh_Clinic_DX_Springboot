package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.staff;
import static com.ngockhanh.clinic.healthexamination.RosterFixtures.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantChangeSupport;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Shared mocks and synthetic data of the manual Participant use case tests. */
abstract class ManualParticipantUseCaseTestBase {
  static final String IDENTIFICATION = "012345678901";
  static final String FULL_NAME = "Synthetic Person";
  static final String PHONE = "0912345678";
  static final String EMAIL = "person@example.test";

  final OrganizationRepository organizations = mock(OrganizationRepository.class);
  final HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
  final HealthExaminationBatchParticipantRepository participants =
      mock(HealthExaminationBatchParticipantRepository.class);
  final AuditWriter audit = mock(AuditWriter.class);
  final ParticipantAccessPolicy access = new ParticipantAccessPolicy();
  final ParticipantChangeSupport support =
      new ParticipantChangeSupport(
          organizations, batches, participants, audit, Clock.fixed(NOW, ZoneOffset.UTC));
  final UUID organizationId = UUID.randomUUID();
  final UUID batchId = UUID.randomUUID();
  final UserPrincipal manager;
  HealthExaminationBatch batch;

  ManualParticipantUseCaseTestBase(String permission) {
    manager = staff(permission);
  }

  /** Stubs an active organization and a READY batch with two days. */
  void givenOpenBatch() {
    givenBatch(BatchStatus.READY, null, true);
  }

  void givenBatch(BatchStatus status, Instant deletedAt, boolean organizationActive) {
    batch = batch(organizationId, batchId, status, 5, deletedAt, UUID.randomUUID());
    when(batches.findDetails(organizationId, batchId, true))
        .thenReturn(Optional.of(details(batch)));
    when(batches.findDetails(organizationId, batchId, false))
        .thenReturn(Optional.of(details(batch)));
    var organization =
        organizationActive ? organization(organizationId) : inactiveOrganization(organizationId);
    when(organizations.findById(eq(new AggregateId(organizationId))))
        .thenReturn(Optional.of(organization));
  }

  UUID firstDay() {
    return batch.days().get(0).id();
  }

  UUID secondDay() {
    return batch.days().get(1).id();
  }

  static Roster roster(String identification) {
    return new Roster(
        "NV-001",
        FULL_NAME,
        LocalDate.of(1990, 5, 12),
        "MALE",
        IdentificationNumber.of(identification),
        PHONE,
        EMAIL,
        "Accounting",
        "Staff");
  }

  /** A stored Participant of the batch on its first day at the given version. */
  HealthExaminationBatchParticipant stored(long version, Progress progress) {
    return HealthExaminationBatchParticipant.restore(
        new AggregateId(UUID.randomUUID()),
        new AggregateId(batchId),
        new AggregateId(firstDay()),
        roster(IDENTIFICATION),
        progress,
        null,
        null,
        NOW,
        NOW,
        version,
        List.of());
  }

  HealthExaminationBatchParticipant stored(long version) {
    return stored(version, progress(null, RosterStatus.ACTIVE, null, null));
  }

  static Progress progress(
      AggregateId patientId, RosterStatus roster, Instant preparedAt, AttendanceStatus attended) {
    boolean attendedOnDay = attended == AttendanceStatus.ATTENDED;
    return new Progress(
        patientId,
        roster,
        attended == null ? AttendanceStatus.UNCONFIRMED : attended,
        attendedOnDay ? LocalDate.of(2026, 10, 4) : null,
        attendedOnDay ? new AggregateId(UUID.randomUUID()) : null,
        attendedOnDay ? NOW : null,
        null,
        ReconciliationStatus.PENDING,
        null,
        null,
        preparedAt);
  }

  void givenStored(HealthExaminationBatchParticipant participant) {
    when(participants.findInBatch(any(), eq(participant.getId())))
        .thenReturn(Optional.of(participant));
  }
}
