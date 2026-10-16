package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.AgreementConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.AgreementSupport;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Replaces the whole configuration of a draft health examination agreement using optimistic
 * locking.
 *
 * <p>The agreement header is locked while the update runs. Services that are kept keep their
 * identifiers and price snapshots; retired lines are history and stay untouched. A service that is
 * removed must not be referenced by any Participant. The changes and the audit event are written in
 * one transaction, so a stale version, a conflict or an audit failure rolls back everything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateHealthExaminationAgreementUseCase {
  private static final String CURRENCY = "VND";

  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final AgreementSupport support;
  private final AuditWriter audit;

  /**
   * Updates a draft agreement when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it may be inactive
   * @param agreementId agreement identifier
   * @param command full replacement configuration and the expected row version
   * @param actor authenticated account performing the update
   * @return the agreement as stored, with the incremented row version
   * @throws IllegalArgumentException when an argument or the configuration is invalid
   * @throws ResourceNotFoundException when the organization or the agreement does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the agreement is not a draft, a new service is not available,
   *     or a removed service is referenced by a Participant
   */
  @Transactional
  public AgreementDetailResponse execute(
      UUID organizationId,
      UUID agreementId,
      UpdateHealthExaminationAgreementCommand command,
      UUID actor) {
    if (organizationId == null
        || agreementId == null
        || command == null
        || command.configuration() == null
        || actor == null)
      throw new IllegalArgumentException(
          "Organization ID, agreement ID, configuration, and updater are required");
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

    var before = support.toResponse(current);
    var configuration = command.configuration();
    var lines = lines(agreement.id(), configuration.services(), agreement.services());
    rejectReferencedRemovals(agreementId, agreement.services(), lines);
    agreement.updateDraft(
        configuration.agreementName(), configuration.validFrom(), configuration.validTo(), lines);

    agreements.update(agreement, expectedVersion);
    var stored =
        agreements
            .findDetails(organizationId, agreementId, false)
            .orElseThrow(
                () -> new IllegalStateException("Updated agreement could not be read back"));
    var after = support.toResponse(stored);
    audit.record(
        actor,
        "UPDATE_HEALTH_EXAMINATION_AGREEMENT",
        "HEALTH_EXAMINATION_AGREEMENT",
        agreementId,
        Map.of(
            "organizationId", organizationId,
            "status", before.status(),
            "rowVersion", before.rowVersion(),
            "configuration", before.auditSummary()),
        Map.of(
            "organizationId", organizationId,
            "status", after.status(),
            "rowVersion", after.rowVersion(),
            "configuration", after.auditSummary()));
    log.info(
        "Health examination agreement update pending commit: organizationId={}, agreementId={},"
            + " services={}",
        organizationId,
        agreementId,
        after.services().size());
    return after;
  }

  private void rejectReferencedRemovals(
      UUID agreementId,
      List<HealthExaminationAgreementService> currentLines,
      List<HealthExaminationAgreementService> targetLines) {
    Set<UUID> kept = new HashSet<>();
    targetLines.forEach(line -> kept.add(line.id().value()));
    Set<UUID> removed = new HashSet<>();
    currentLines.forEach(
        line -> {
          if (!kept.contains(line.id().value())) removed.add(line.id().value());
        });
    if (!agreements.findReferencedAgreementServiceIds(agreementId, removed).isEmpty())
      throw new DomainRuleViolation("A removed agreement service is used by Participants");
  }

  /**
   * Builds the target line list: the requested services in order (kept lines keep their identity
   * and reference price, new ones come from the catalog), then the retired lines as history.
   */
  private List<HealthExaminationAgreementService> lines(
      AggregateId agreementId,
      List<AgreementConfiguration.ServicePrice> requested,
      List<HealthExaminationAgreementService> currentLines) {
    if (requested == null || requested.isEmpty())
      throw new IllegalArgumentException("At least one service is required");
    Map<UUID, HealthExaminationAgreementService> active = new HashMap<>();
    List<HealthExaminationAgreementService> retired = new ArrayList<>();
    for (var line : currentLines) {
      if (line.active()) active.put(line.serviceId().value(), line);
      else retired.add(line);
    }
    Set<UUID> seen = new HashSet<>();
    for (var item : requested) {
      if (item == null || item.serviceId() == null || item.negotiatedPrice() == null)
        throw new IllegalArgumentException("Service and negotiated price are required");
      if (!seen.add(item.serviceId())) throw new DomainRuleViolation("Duplicate agreement service");
    }
    List<AgreementConfiguration.ServicePrice> added =
        requested.stream().filter(item -> !active.containsKey(item.serviceId())).toList();
    Map<UUID, HealthExaminationAgreementService> created = new HashMap<>();
    if (!added.isEmpty())
      for (var line : support.newLines(agreementId, added, 1))
        created.put(line.serviceId().value(), line);

    List<HealthExaminationAgreementService> lines = new ArrayList<>();
    int displayOrder = 1;
    for (var item : requested) {
      Money negotiated = new Money(item.negotiatedPrice(), CURRENCY);
      var kept = active.get(item.serviceId());
      if (kept != null) {
        lines.add(
            new HealthExaminationAgreementService(
                kept.id(),
                kept.serviceId(),
                agreementId,
                kept.referencePriceSnapshot(),
                negotiated,
                displayOrder++,
                true,
                kept.rowVersion()));
        continue;
      }
      var line = created.get(item.serviceId());
      lines.add(
          new HealthExaminationAgreementService(
              line.id(),
              line.serviceId(),
              agreementId,
              line.referencePriceSnapshot(),
              line.negotiatedPrice(),
              displayOrder++,
              true,
              0));
    }
    for (var line : retired)
      lines.add(
          new HealthExaminationAgreementService(
              line.id(),
              line.serviceId(),
              agreementId,
              line.referencePriceSnapshot(),
              line.negotiatedPrice(),
              displayOrder++,
              false,
              line.rowVersion()));
    return lines;
  }
}
