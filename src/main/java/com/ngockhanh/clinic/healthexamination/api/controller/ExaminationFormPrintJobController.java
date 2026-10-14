package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.api.request.CreateExaminationFormPrintJobRequest;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationFormPrintJobResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateExaminationFormPrintJobUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prints the blank examination forms of many Participants of a batch in a background job. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(
    "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants")
public class ExaminationFormPrintJobController {
  private final CreateExaminationFormPrintJobUseCase useCase;

  /**
   * Queues the print job.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param request the Participants to print (listed, or all of an examination day)
   * @param idempotencyKey client-chosen key; a retry with the same key and body returns the same job
   * @param principal authenticated staff principal
   * @return 202 with the job identifier and a {@code Location} header to poll
   */
  @PostMapping("/examination-form-print-jobs")
  public ResponseEntity<ApiResponse<ExaminationFormPrintJobResponse>> create(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @Valid @RequestBody CreateExaminationFormPrintJobRequest request,
      @RequestHeader("Idempotency-Key") UUID idempotencyKey,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Examination form print job request: organizationId={}, batchId={}, scope={}",
        organizationId,
        batchId,
        request.scope() == null ? "IDS" : request.scope());
    ExaminationFormPrintJobResponse response =
        useCase.execute(organizationId, batchId, request.toCommand(idempotencyKey), principal);
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/document-render-jobs/" + response.jobId()))
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.ACCEPTED.value(), response));
  }
}
