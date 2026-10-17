package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.RecordExaminationDetailCommand;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailRowResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailEntry;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailReconciler;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the services performed for one Participant by hand.
 *
 * <p>The caller must hold the service record permission. The entry is exactly one row of the Excel
 * import: it goes through {@link ExaminationDetailReconciler}, so the Participant becomes {@code
 * RECONCILED}, unticked services keep their row with {@code performed = false}, a newly ticked
 * service takes the negotiated price, and a service that is no longer offered cannot be newly
 * ticked. Attendance is not recorded here: it belongs to the session assignments.
 *
 * <p>Everything runs in one transaction that locks the agreement (so a manual entry and an import
 * never interleave) and then the Participant. The row version must match; the single exception is a
 * retry: when the stored state already is the requested state the stored row is returned and
 * nothing is written, so a client that lost the response of a successful request can send it again
 * without an idempotency key. A request that equals the stored state writes nothing and does not
 * raise the row version. The audit event carries identifiers and counts only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordExaminationDetailUseCase {
  private static final String ACTION = "RECORD_EXAMINATION_DETAIL";

  private final ExaminationDetailAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final HealthExaminationParticipantRepository participants;
  private final ExaminationDetailReader reader;
  private final AuditWriter audit;
  private final Clock clock;

  private final ExaminationDetailReconciler reconciler = new ExaminationDetailReconciler();

  /**
   * Records the entry and returns the Participant row as it is stored afterwards.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param participantId Participant identifier
   * @param command performed services and the row version the client saw
   * @param principal authenticated staff account
   * @return the up-to-date row, with the identification number still complete in the read model and
   *     masked by the response
   * @throws ApplicationException {@code ACCESS_DENIED} without the record permission, or {@code
   *     INVALID_INPUT} when a service is outside the agreement
   * @throws IllegalArgumentException when an identifier or the command is null
   * @throws ResourceNotFoundException when the organization, the agreement or the Participant is
   *     not found
   * @throws ConflictException when the organization or agreement does not accept changes, the
   *     Participant is cancelled, or a newly ticked service is no longer offered
   * @throws ConcurrentUpdateException when the Participant changed since the client read it and the
   *     stored state is not already the requested one
   */
  @Transactional
  public ExaminationDetailRowResponse execute(
      UUID organizationId,
      UUID agreementId,
      UUID participantId,
      RecordExaminationDetailCommand command,
      UserPrincipal principal) {
    access.requireServiceRecord(principal);
    if (organizationId == null || agreementId == null || participantId == null || command == null)
      throw new IllegalArgumentException(
          "Organization, agreement and Participant IDs and command are required");

    var details =
        agreements
            .findDetails(organizationId, agreementId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));
    var agreement = details.agreement();
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    if (organization.status() != OrganizationStatus.ACTIVE
        || !agreement.acceptsParticipantChanges())
      throw new ConflictException("Agreement does not accept examination detail changes");

    Set<UUID> agreementServiceIds = new HashSet<>();
    for (var service : agreement.services()) agreementServiceIds.add(service.id().value());
    if (!agreementServiceIds.containsAll(command.performedAgreementServiceIds()))
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT,
          "performedAgreementServiceIds contains a service outside this agreement");

    HealthExaminationParticipant participant =
        participants
            .findManyInAgreementForUpdate(
                AggregateId.of(agreementId), List.of(AggregateId.of(participantId)))
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (participant.getRosterStatus() != RosterStatus.ACTIVE)
      throw new ConflictException("Participant is cancelled");

    if (participant.getRowVersion() != command.rowVersion()) {
      if (alreadyRecorded(participant, command))
        return currentRow(organizationId, agreementId, participantId);
      throw new ConcurrentUpdateException(
          "PARTICIPANT_CHANGED",
          "Participant was changed by another request; reload and try again");
    }

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    var entry =
        new ExaminationDetailEntry(
            participant.getId().value(),
            command.rowVersion(),
            command.performedAgreementServiceIds(),
            "Participant");
    boolean written =
        reconciler.apply(participant, agreement, entry, AggregateId.of(principal.userId()), now)
            == ExaminationDetailReconciler.Result.UPDATED;
    if (written) {
      participants.save(participant, command.rowVersion());
      audit.record(
          principal.userId(),
          ACTION,
          "HEALTH_EXAMINATION_PARTICIPANT",
          participantId,
          null,
          Map.of(
              "organizationId", organizationId,
              "agreementId", agreementId,
              "participantId", participantId,
              "performedItems", command.performedAgreementServiceIds().size()));
    }
    log.info(
        "Examination detail recorded: organizationId={}, agreementId={}, participantId={}, result={}",
        organizationId,
        agreementId,
        participantId,
        written ? "UPDATED" : "UNCHANGED");
    return currentRow(organizationId, agreementId, participantId);
  }

  /** Whether the stored state already is what the command asks for (a retry of a won request). */
  private static boolean alreadyRecorded(
      HealthExaminationParticipant participant, RecordExaminationDetailCommand command) {
    Set<UUID> performed = new HashSet<>();
    for (AggregateId id : participant.performedAgreementServiceIds()) performed.add(id.value());
    return participant.getReconciliationStatus() == ReconciliationStatus.RECONCILED
        && performed.equals(command.performedAgreementServiceIds());
  }

  private ExaminationDetailRowResponse currentRow(
      UUID organizationId, UUID agreementId, UUID participantId) {
    return ExaminationDetailRowResponse.from(
        reader
            .readOne(organizationId, agreementId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant")));
  }
}
