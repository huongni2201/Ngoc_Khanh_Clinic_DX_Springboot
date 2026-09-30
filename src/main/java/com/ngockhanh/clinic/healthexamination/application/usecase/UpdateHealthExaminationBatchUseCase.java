package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.BatchConfigurationLocked;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.shared.audit.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateHealthExaminationBatchUseCase {
  private final HealthExaminationBatchRepository batches;
  private final BatchDraftEditor editor;
  private final AuditWriter audit;

  @Transactional
  public BatchDetailResponse execute(UUID org, UUID id, BatchConfigurationCommand c, UUID actor) {
    if (actor == null) throw new IllegalArgumentException("Actor is required");
    var details =
        batches
            .findDetails(org, id, true)
            .filter(d -> d.batch().status() != BatchStatus.DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("Batch not found"));
    var b = details.batch();
    if (b.status() != BatchStatus.DRAFT) throw new BatchConfigurationLocked();
    var before = BatchDetailResponse.from(details).auditSummary();
    var site = BatchDraftEditor.site(c);
    b.updateDraft(
        c.batchCode(),
        c.batchName(),
        c.startDate(),
        c.endDate(),
        c.reason(),
        c.payerType(),
        site,
        editor.services(b.id(), c.services(), b.services()));
    batches.update(b);
    var result = BatchDetailResponse.from(batches.findDetails(org, id, false).orElseThrow());
    audit.record(
        actor,
        "UPDATE_HEALTH_EXAMINATION_BATCH",
        "HEALTH_EXAMINATION_BATCH",
        id,
        before,
        result.auditSummary());
    log.info("Batch update persisted: organizationId={}, batchId={}", org, id);
    return result;
  }
}
