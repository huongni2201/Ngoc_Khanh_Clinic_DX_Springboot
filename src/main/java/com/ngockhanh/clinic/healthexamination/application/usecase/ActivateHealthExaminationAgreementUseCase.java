package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.ActivateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.AgreementSupport;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
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
 * Activates a draft health examination agreement: DRAFT becomes ACTIVE (UC-071).
 *
 * <p>An active agreement keeps accepting new services; only a draft can be fully re-configured or
 * deleted. Participants can still be added, edited or cancelled, sessions scheduled and the blank
 * examination forms printed. The agreement header is locked while the transition runs, and the
 * transition and its audit event are written in one transaction, so an audit failure rolls the
 * transition back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateHealthExaminationAgreementUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final AgreementSupport support;
  private final AuditWriter audit;

  /**
   * Activates a draft agreement when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it must exist and be active
   * @param agreementId agreement identifier
   * @param command carries the row version the caller last read
   * @param actor authenticated account activating the agreement
   * @return the agreement as stored, ACTIVE with the incremented row version
   * @throws IllegalArgumentException when an argument is null or the row version is negative
   * @throws ResourceNotFoundException when the organization or the agreement does not exist, or the
   *     agreement is deleted
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the organization is inactive or the agreement is not a draft
   */
  @Transactional
  public AgreementDetailResponse execute(
      UUID organizationId,
      UUID agreementId,
      ActivateHealthExaminationAgreementCommand command,
      UUID actor) {
    if (organizationId == null || agreementId == null || command == null || actor == null)
      throw new IllegalArgumentException(
          "Organization ID, agreement ID, command, and actor are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    if (organization.status() != OrganizationStatus.ACTIVE)
      throw new DomainRuleViolation("An agreement of an inactive organization cannot be activated");
    var current =
        agreements
            .findDetails(organizationId, agreementId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));
    var agreement = current.agreement();
    if (expectedVersion != agreement.rowVersion()) throw new ConcurrentUpdateException();

    agreement.activate();
    agreements.updateStatus(agreement, AgreementStatus.DRAFT, expectedVersion);
    var stored =
        agreements
            .findDetails(organizationId, agreementId, false)
            .orElseThrow(
                () -> new IllegalStateException("Activated agreement could not be read back"));
    var after = support.toResponse(stored);
    audit.record(
        actor,
        "ACTIVATE_HEALTH_EXAMINATION_AGREEMENT",
        "HEALTH_EXAMINATION_AGREEMENT",
        agreementId,
        Map.of(
            "organizationId", organizationId,
            "status", AgreementStatus.DRAFT.name(),
            "rowVersion", expectedVersion),
        Map.of(
            "organizationId", organizationId,
            "status", after.status(),
            "rowVersion", after.rowVersion()));
    log.info(
        "Health examination agreement activation pending commit: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    return after;
  }
}
