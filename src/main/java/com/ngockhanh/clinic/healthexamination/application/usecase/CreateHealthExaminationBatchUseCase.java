package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.*;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateHealthExaminationBatchUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final BatchDraftEditor editor;
  private final AuditWriter audit;

  @Transactional
  public BatchDetailResponse execute(
      UUID organizationId, CreateHealthExaminationBatchCommand command) {
    if (command == null || command.createdBy() == null)
      throw new IllegalArgumentException("Batch creator is required");
    var org =
        organizations
            .findById(new AggregateId(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    if (!"ACTIVE".equals(org.status())) throw new BusinessRuleException("Organization is inactive");
    var c = command.configuration();
    var site = BatchDraftEditor.site(c);
    var id = new AggregateId(UuidV7Generator.generate());
    var batch =
        HealthExaminationBatch.createDraft(
            id,
            org.id(),
            c.batchCode(),
            c.batchName(),
            site,
            editor.days(c.examinationDates(), java.util.List.of()),
            editor.services(id, c.services(), java.util.List.of()));
    batches.insert(batch, command.createdBy());
    var result =
        BatchDetailResponse.from(
            batches.findDetails(organizationId, id.value(), false).orElseThrow());
    audit.record(
        command.createdBy(),
        "CREATE_HEALTH_EXAMINATION_BATCH",
        "HEALTH_EXAMINATION_BATCH",
        id.value(),
        null,
        result.auditSummary());
    log.info("Batch creation persisted: organizationId={}, batchId={}", organizationId, id.value());
    return result;
  }
}
