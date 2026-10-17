package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.EndHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.AgreementSupport;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends an active health examination agreement: ACTIVE becomes ENDED (UC-072).
 *
 * <p>An ended agreement keeps its roster, schedule, reconciliation and reports readable, but
 * accepts no further change. The agreement header is locked while the transition runs, and the
 * transition and its audit event are written in one transaction, so an audit failure rolls the
 * transition back. The organization may be inactive: ending an agreement is how its work is closed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndHealthExaminationAgreementUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final AgreementSupport support;
  private final AuditWriter audit;

  /**
   * Ends an active agreement when the caller's expected row version is still current.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param command carries the row version the caller last read
   * @param actor authenticated account ending the agreement
   * @return the agreement as stored, ENDED with the incremented row version
   * @throws IllegalArgumentException when an argument is null or the row version is negative
   * @throws ResourceNotFoundException when the organization or the agreement does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the agreement is not active
   */
  @Transactional
  public AgreementDetailResponse execute(
      UUID organizationId,
      UUID agreementId,
      EndHealthExaminationAgreementCommand command,
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

    agreement.end();
    agreements.updateStatus(agreement, AgreementStatus.ACTIVE, expectedVersion);
    var stored =
        agreements
            .findDetails(organizationId, agreementId, false)
            .orElseThrow(() -> new IllegalStateException("Ended agreement could not be read back"));
    var after = support.toResponse(stored);
    audit.record(
        actor,
        "END_HEALTH_EXAMINATION_AGREEMENT",
        "HEALTH_EXAMINATION_AGREEMENT",
        agreementId,
        Map.of(
            "organizationId", organizationId,
            "status", AgreementStatus.ACTIVE.name(),
            "rowVersion", expectedVersion),
        Map.of(
            "organizationId", organizationId,
            "status", after.status(),
            "rowVersion", after.rowVersion()));
    log.info(
        "Health examination agreement end pending commit: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    return after;
  }
}
