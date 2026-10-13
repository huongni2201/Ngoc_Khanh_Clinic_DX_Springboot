package com.ngockhanh.clinic.document.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.RenderJobStore;
import com.ngockhanh.clinic.document.application.rendering.RenderJobs;
import com.ngockhanh.clinic.document.application.response.RenderJobPartResponse;
import com.ngockhanh.clinic.document.application.service.RenderJobAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Gives one merged PDF part of a finished print job to the account that created it. The download is
 * audited with identifiers only.
 */
@Service
@RequiredArgsConstructor
public class DownloadRenderJobPartUseCase {
  private final RenderJobAccessPolicy access;
  private final RenderJobStore store;
  private final DocumentStorage storage;
  private final AuditWriter audit;

  /**
   * @param jobId job identifier
   * @param partNo part number, starting at 1
   * @param principal authenticated staff caller
   * @return the PDF and its download name
   * @throws ApplicationException {@code ACCESS_DENIED} without the download permission
   * @throws IllegalArgumentException when an identifier is missing or the part number is below 1
   * @throws ResourceNotFoundException when the job, the part or its file is gone, the job belongs
   *     to someone else, or it has expired
   * @throws ConflictException when the job has not finished successfully
   */
  public RenderJobPartResponse execute(UUID jobId, int partNo, UserPrincipal principal) {
    access.requireDownload(principal);
    if (jobId == null || partNo < 1) throw new IllegalArgumentException("Job and part are required");
    var job =
        store
            .findById(jobId)
            .filter(found -> found.requestedBy().equals(principal.userId()))
            .orElseThrow(() -> new ResourceNotFoundException("Print job"));
    if ("EXPIRED".equals(job.status())) throw new ResourceNotFoundException("Print job part");
    if (!"SUCCEEDED".equals(job.status()))
      throw new ConflictException("The print job has not finished successfully");
    var part =
        store.findParts(jobId).stream()
            .filter(p -> p.partNo() == partNo)
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Print job part"));
    byte[] content = storage.read(part.storageProvider(), part.storageKey(), part.checksum());
    audit.record(
        principal.userId(),
        "DOWNLOAD_RENDER_JOB_PART",
        "DOCUMENT_RENDER_JOB",
        jobId,
        null,
        Map.of("partNo", partNo, "documentType", job.documentType()));
    return new RenderJobPartResponse(content, fileName(job.context().get(RenderJobs.FILE_NAME_KEY), partNo));
  }

  private static String fileName(String base, int partNo) {
    String stem = base == null || base.isBlank() ? "documents" : base.strip();
    if (stem.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
      stem = stem.substring(0, stem.length() - 4);
    return stem + "-phan-" + partNo + ".pdf";
  }
}
