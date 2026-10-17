package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.AgreementFixtures.*;
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
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
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
  final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);
  final HealthExaminationParticipantRepository participants =
      mock(HealthExaminationParticipantRepository.class);
  final HealthExaminationSessionRepository sessions =
      mock(HealthExaminationSessionRepository.class);
  final SessionAssignmentRepository assignments = mock(SessionAssignmentRepository.class);
  final AuditWriter audit = mock(AuditWriter.class);
  final ParticipantAccessPolicy access = new ParticipantAccessPolicy();
  final ParticipantChangeSupport support =
      new ParticipantChangeSupport(
          organizations,
          agreements,
          participants,
          assignments,
          audit,
          Clock.fixed(NOW, ZoneOffset.UTC));
  final UUID organizationId = UUID.randomUUID();
  final UUID agreementId = UUID.randomUUID();
  final UserPrincipal manager;
  HealthExaminationAgreement agreement;

  ManualParticipantUseCaseTestBase(String permission) {
    manager = staff(permission);
  }

  /** Stubs an active organization and an ACTIVE agreement. */
  void givenOpenAgreement() {
    givenAgreement(AgreementStatus.ACTIVE, null, true);
  }

  void givenAgreement(AgreementStatus status, Instant deletedAt, boolean organizationActive) {
    agreement = agreement(organizationId, agreementId, status, 5, deletedAt, UUID.randomUUID());
    when(agreements.findDetails(organizationId, agreementId, true))
        .thenReturn(Optional.of(details(agreement)));
    when(agreements.findDetails(organizationId, agreementId, false))
        .thenReturn(Optional.of(details(agreement)));
    var organization =
        organizationActive ? organization(organizationId) : inactiveOrganization(organizationId);
    when(organizations.findById(eq(new AggregateId(organizationId))))
        .thenReturn(Optional.of(organization));
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

  /** A stored Participant of the agreement at the given version. */
  HealthExaminationParticipant stored(long version, Progress progress) {
    return HealthExaminationParticipant.restore(
        new AggregateId(UUID.randomUUID()),
        new AggregateId(organizationId),
        new AggregateId(agreementId),
        roster(IDENTIFICATION),
        progress,
        null,
        null,
        NOW,
        NOW,
        version,
        List.of());
  }

  HealthExaminationParticipant stored(long version) {
    return stored(version, progress(null, RosterStatus.ACTIVE, null));
  }

  static Progress progress(AggregateId patientId, RosterStatus roster, Instant preparedAt) {
    return new Progress(patientId, roster, ReconciliationStatus.PENDING, null, null, preparedAt);
  }

  void givenStored(HealthExaminationParticipant participant) {
    when(participants.findInAgreement(any(), eq(participant.getId())))
        .thenReturn(Optional.of(participant));
  }
}
