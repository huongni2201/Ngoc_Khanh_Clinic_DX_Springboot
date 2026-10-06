package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.BatchConfigurationAssembler;
import com.ngockhanh.clinic.healthexamination.application.service.BatchDetailResponseMapper;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a draft health examination batch for an active organization.
 *
 * <p>The header, its days and services, and the audit event are written in one transaction, so a
 * failure of any part rolls back all of it. A concurrent create with the same batch code loses at
 * the database unique constraint, which surfaces as a duplicate-key conflict.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateHealthExaminationBatchUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final BatchConfigurationAssembler assembler;
  private final BatchDetailResponseMapper responses;
  private final AuditWriter audit;

  /**
   * Creates a batch with status {@code DRAFT} and row version 0.
   *
   * @param organizationId owning organization
   * @param command batch configuration
   * @param actor authenticated account creating the batch
   * @return the batch as stored, including timestamps
   * @throws IllegalArgumentException when an argument or the configuration is invalid
   * @throws ResourceNotFoundException when the organization does not exist
   * @throws DomainRuleViolation when the organization is inactive or a service is not available
   */
  @Transactional
  public BatchDetailResponse execute(
      UUID organizationId, CreateHealthExaminationBatchCommand command, UUID actor) {
    if (organizationId == null
        || command == null
        || command.configuration() == null
        || actor == null)
      throw new IllegalArgumentException("Organization ID, configuration, and creator are required");
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    if (!"ACTIVE".equals(organization.status()))
      throw new DomainRuleViolation("Organization is not active");

    var batchId = new AggregateId(UuidV7Generator.generate());
    var configuration = assembler.forCreate(batchId, command.configuration());
    var batch =
        HealthExaminationBatch.createDraft(
            batchId,
            organization.id(),
            configuration.batchCode(),
            configuration.batchName(),
            configuration.site(),
            configuration.days(),
            configuration.services());
    batches.insert(batch, actor);
    var stored =
        batches
            .findDetails(organizationId, batchId.value(), false)
            .orElseThrow(() -> new IllegalStateException("Created batch could not be read back"));
    var response = responses.toResponse(stored);
    audit.record(
        actor,
        "CREATE_HEALTH_EXAMINATION_BATCH",
        "HEALTH_EXAMINATION_BATCH",
        batchId.value(),
        null,
        Map.of(
            "organizationId", organizationId,
            "status", response.status(),
            "rowVersion", response.rowVersion(),
            "configuration", response.auditSummary()));
    log.info(
        "Health examination batch creation pending commit: organizationId={}, batchId={}, days={},"
            + " services={}",
        organizationId,
        batchId.value(),
        response.days().size(),
        response.services().size());
    return response;
  }
}
