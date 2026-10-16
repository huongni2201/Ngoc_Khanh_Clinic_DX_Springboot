package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.integration.application.query.AgreementHistoryQuery;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft-deletes a draft health examination agreement that has no Participant, no session and no
 * integration history.
 *
 * <p>Nothing is physically removed: the agreement row, its services, and its agreement code are
 * kept, and the agreement disappears from every read. The agreement header is locked while the
 * checks and the deletion run. The deletion and its audit event are written in one transaction, so
 * an audit failure rolls the deletion back. Deleting an already deleted agreement reports it as not
 * found.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteHealthExaminationAgreementUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final AgreementHistoryQuery agreementHistory;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Soft-deletes an agreement when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it may be inactive
   * @param agreementId agreement identifier
   * @param command carries the row version the caller last read
   * @param actor authenticated account performing the deletion
   * @throws IllegalArgumentException when an argument is null or the row version is negative
   * @throws ResourceNotFoundException when the organization or the agreement does not exist, or the
   *     agreement is already deleted
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the agreement is not a draft, has any Participant (whatever
   *     its roster status) or session, or is referenced by integration history
   */
  @Transactional
  public void execute(
      UUID organizationId,
      UUID agreementId,
      DeleteHealthExaminationAgreementCommand command,
      UUID actor) {
    if (organizationId == null || agreementId == null || command == null || actor == null)
      throw new IllegalArgumentException(
          "Organization ID, agreement ID, command, and actor are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var current =
        agreements
            .findDetails(organizationId, agreementId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));
    var agreement = current.agreement();
    if (expectedVersion != agreement.rowVersion()) throw new ConcurrentUpdateException();
    agreement.requireDraft();
    if (agreements.hasParticipants(agreementId))
      throw new DomainRuleViolation("An agreement with Participants cannot be deleted");
    if (agreements.hasSessions(agreementId))
      throw new DomainRuleViolation("An agreement with sessions cannot be deleted");
    if (agreementHistory.hasAgreementReferences(agreementId))
      throw new DomainRuleViolation("An agreement with integration history cannot be deleted");

    Instant deletedAt = Instant.now(clock);
    agreement.softDelete(deletedAt);
    agreements.softDelete(agreement, expectedVersion);
    audit.record(
        actor,
        "DELETE_HEALTH_EXAMINATION_AGREEMENT",
        "HEALTH_EXAMINATION_AGREEMENT",
        agreementId,
        Map.of(
            "organizationId", organizationId,
            "status", agreement.status().name(),
            "rowVersion", expectedVersion),
        Map.of(
            "organizationId",
            organizationId,
            "status",
            agreement.status().name(),
            "rowVersion",
            expectedVersion + 1,
            "deletedAt",
            deletedAt.toString()));
    log.info(
        "Health examination agreement deletion pending commit: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
  }
}
