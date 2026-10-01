package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.shared.audit.AuditWriter;
import com.ngockhanh.clinic.shared.exception.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteHealthExaminationBatchUseCase {
  private final HealthExaminationBatchRepository batches;
  private final AuditWriter audit;

  @Transactional
  public void execute(UUID org, UUID id, UUID actor) {
    if (actor == null) throw new IllegalArgumentException("Actor is required");
    var details =
        batches
            .findDetailsIncludingDeleted(org, id, true)
            .orElseThrow(() -> new ResourceNotFoundException("Batch not found"));
    var b = details.batch();
    if (b.status() == BatchStatus.DELETED) return;
    if (batches.hasDependents(id)) throw new BusinessRuleException("Batch has dependent records");
    var before = BatchDetailResponse.from(details).auditSummary();
    b.deleteDraft();
    batches.update(b);
    audit.record(
        actor,
        "DELETE_HEALTH_EXAMINATION_BATCH",
        "HEALTH_EXAMINATION_BATCH",
        id,
        before,
        BatchDetailResponse.from(details).auditSummary());
    log.info("Batch soft deletion persisted: organizationId={}, batchId={}", org, id);
  }
}
