package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetParticipantImportUseCase {
  private final java.time.Clock clock;
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationImportJobRepository jobs;

  public ParticipantImportSummaryResponse execute(
      UUID organizationId, UUID batchId, UUID importId) {
    log.debug("Read participant import: importId={}, batchId={}", importId, batchId);
    batches
        .findByIdAndOrganizationId(AggregateId.of(batchId), AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    var job =
        jobs.findByIdAndBatchId(AggregateId.of(importId), AggregateId.of(batchId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant import"));
    return ParticipantImportSummaryResponse.from(job, clock.instant());
  }
}
