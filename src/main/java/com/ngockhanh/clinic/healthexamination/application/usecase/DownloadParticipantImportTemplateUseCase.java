package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportTemplateWriter;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadParticipantImportTemplateUseCase {
  private final HealthExaminationBatchRepository batches;
  private final ParticipantImportTemplateWriter templateWriter;

  public byte[] execute(UUID organizationId, UUID batchId) {
    log.debug(
        "Download participant template: organizationId={}, batchId={}", organizationId, batchId);
    if (organizationId == null || batchId == null) {
      throw new IllegalArgumentException("Organization and batch identifiers are required");
    }
    batches
        .findByIdAndOrganizationId(AggregateId.of(batchId), AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    return templateWriter.generate();
  }
}
