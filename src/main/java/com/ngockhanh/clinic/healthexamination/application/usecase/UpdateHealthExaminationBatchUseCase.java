package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.BatchConfigurationLocked;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.integration.application.imports.ImportStore;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
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
  private final ImportStore imports;

  @Transactional
  public BatchDetailResponse execute(UUID org, UUID id, BatchConfigurationCommand c, UUID actor) {
    if (actor == null) throw new IllegalArgumentException("Actor is required");
    if (c == null) throw new IllegalArgumentException("Batch configuration is required");
    var details =
        batches
            .findDetails(org, id, true)
            .orElseThrow(() -> new ResourceNotFoundException("Batch not found"));
    var b = details.batch();
    if (c.rowVersion() == null || c.rowVersion() != b.rowVersion())
      throw new ConcurrentUpdateException();
    if (b.status() != BatchStatus.DRAFT) throw new BatchConfigurationLocked();
    var desiredDays = editor.days(c.examinationDates(), b.days());
    var desiredDayIds =
        desiredDays.stream().map(day -> day.id()).collect(java.util.stream.Collectors.toSet());
    var removedDayIds =
        b.days().stream()
            .map(day -> day.id())
            .filter(dayId -> !desiredDayIds.contains(dayId))
            .toList();
    if (!removedDayIds.isEmpty() && imports.hasBatchDayReferences(id, removedDayIds))
      throw new BusinessRuleException("Batch day has import records");
    var before = BatchDetailResponse.from(details).auditSummary();
    var site = BatchDraftEditor.site(c);
    b.updateDraft(
        c.batchCode(),
        c.batchName(),
        site,
        desiredDays,
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
