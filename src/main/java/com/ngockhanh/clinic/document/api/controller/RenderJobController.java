package com.ngockhanh.clinic.document.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.document.application.response.RenderJobPartResponse;
import com.ngockhanh.clinic.document.application.response.RenderJobResponse;
import com.ngockhanh.clinic.document.application.usecase.DownloadRenderJobPartUseCase;
import com.ngockhanh.clinic.document.application.usecase.GetRenderJobUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows the progress of a background print job and hands out its merged PDF parts. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/document-render-jobs")
public class RenderJobController {
  private final GetRenderJobUseCase getJob;
  private final DownloadRenderJobPartUseCase downloadPart;

  /**
   * Shows a job created by the caller.
   *
   * @param jobId job identifier
   * @param principal authenticated staff principal
   * @return 200 with the progress, skipped records and, once finished, the parts; {@code no-store}
   */
  @GetMapping("/{jobId}")
  public ResponseEntity<ApiResponse<RenderJobResponse>> job(
      @PathVariable UUID jobId, @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Print job request: jobId={}", jobId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.OK.value(), getJob.execute(jobId, principal)));
  }

  /**
   * Downloads one merged PDF part of a finished job created by the caller.
   *
   * @param jobId job identifier
   * @param partNo part number, starting at 1
   * @param principal authenticated staff principal
   * @return 200 with the PDF as an attachment and {@code no-store}, since it holds personal data
   */
  @GetMapping("/{jobId}/parts/{partNo}")
  public ResponseEntity<byte[]> part(
      @PathVariable UUID jobId,
      @PathVariable int partNo,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Print job part request: jobId={}, partNo={}", jobId, partNo);
    RenderJobPartResponse file = downloadPart.execute(jobId, partNo, principal);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(file.content());
  }
}
