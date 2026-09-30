package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetHealthExaminationBatchUseCase {
  private final HealthExaminationBatchRepository batches;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public BatchDetailResponse execute(UUID organizationId, UUID batchId) {
    log.debug("Get batch: organizationId={}, batchId={}", organizationId, batchId);
    return BatchDetailResponse.from(
        batches
            .findDetails(organizationId, batchId, false)
            .filter(d -> d.batch().status() != BatchStatus.DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("Batch not found")));
  }
}
