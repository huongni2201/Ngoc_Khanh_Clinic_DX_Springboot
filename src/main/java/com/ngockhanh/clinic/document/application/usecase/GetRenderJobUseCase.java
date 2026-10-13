package com.ngockhanh.clinic.document.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.document.application.port.RenderJobStore;
import com.ngockhanh.clinic.document.application.response.RenderJobResponse;
import com.ngockhanh.clinic.document.application.service.RenderJobAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Shows the progress of a print job to the account that created it. Another account gets the same
 * answer as for a job that does not exist.
 */
@Service
@RequiredArgsConstructor
public class GetRenderJobUseCase {
  private final RenderJobAccessPolicy access;
  private final RenderJobStore store;

  /**
   * @param jobId job identifier
   * @param principal authenticated staff caller
   * @return the job with its skipped records and, once finished, its downloadable parts
   * @throws ApplicationException {@code ACCESS_DENIED} without the read permission
   * @throws IllegalArgumentException when the identifier is null
   * @throws ResourceNotFoundException when the job does not exist or belongs to someone else
   */
  public RenderJobResponse execute(UUID jobId, UserPrincipal principal) {
    access.requireRead(principal);
    if (jobId == null) throw new IllegalArgumentException("Job ID is required");
    var job =
        store
            .findById(jobId)
            .filter(found -> found.requestedBy().equals(principal.userId()))
            .orElseThrow(() -> new ResourceNotFoundException("Print job"));
    List<RenderJobResponse.Part> parts =
        "SUCCEEDED".equals(job.status())
            ? store.findParts(jobId).stream()
                .map(p -> new RenderJobResponse.Part(p.partNo(), p.recordCount(), p.sizeBytes()))
                .toList()
            : List.of();
    return new RenderJobResponse(
        job.id(),
        job.documentType(),
        job.status(),
        job.totalCount(),
        job.processedCount(),
        job.errorCode(),
        job.skipped().stream().map(s -> new RenderJobResponse.Skipped(s.id(), s.reason())).toList(),
        parts,
        job.createdAt(),
        job.finishedAt(),
        job.expiresAt());
  }
}
