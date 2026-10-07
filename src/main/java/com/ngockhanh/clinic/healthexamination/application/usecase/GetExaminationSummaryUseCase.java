package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts the active Participants of one health examination batch by attendance and reconciliation
 * state.
 *
 * <p>The caller must hold the service read permission, checked before the batch is looked up.
 * Cancelled Participants are never counted. Nothing is written or audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetExaminationSummaryUseCase {
  private final ExaminationDetailAccessPolicy access;
  private final ExaminationDetailReader reader;

  /**
   * Returns the counters of the batch.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param principal authenticated staff account
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the service read permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the batch is not found in the organization
   */
  @Transactional(readOnly = true)
  public ExaminationSummaryResponse execute(
      UUID organizationId, UUID batchId, UserPrincipal principal) {
    access.requireServiceRead(principal);
    if (organizationId == null || batchId == null)
      throw new IllegalArgumentException("Organization ID and batch ID are required");
    var summary =
        reader
            .summarize(organizationId, batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    log.debug(
        "Examination summary read: organizationId={}, batchId={}, registered={}",
        organizationId,
        batchId,
        summary.registered());
    return ExaminationSummaryResponse.from(summary);
  }
}
