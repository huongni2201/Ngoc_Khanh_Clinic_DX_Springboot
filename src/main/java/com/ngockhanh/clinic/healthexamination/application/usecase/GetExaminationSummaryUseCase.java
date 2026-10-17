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
 * Counts the active Participants of one health examination agreement by attendance and
 * reconciliation state.
 *
 * <p>The caller must hold the service read permission, checked before the agreement is looked up.
 * Cancelled Participants are never counted. Nothing is written or audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetExaminationSummaryUseCase {
  private final ExaminationDetailAccessPolicy access;
  private final ExaminationDetailReader reader;

  /**
   * Returns the counters of the agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param principal authenticated staff account
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the service read permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the agreement is not found in the organization
   */
  @Transactional(readOnly = true)
  public ExaminationSummaryResponse execute(
      UUID organizationId, UUID agreementId, UserPrincipal principal) {
    access.requireServiceSummaryRead(principal);
    if (organizationId == null || agreementId == null)
      throw new IllegalArgumentException("Organization ID and agreement ID are required");
    var summary =
        reader
            .summarize(organizationId, agreementId)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));
    log.info(
        "Examination summary read: organizationId={}, agreementId={}, registered={}",
        organizationId,
        agreementId,
        summary.registered());
    return ExaminationSummaryResponse.from(summary);
  }
}
