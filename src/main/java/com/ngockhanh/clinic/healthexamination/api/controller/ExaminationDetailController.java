package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.api.request.ExaminationDetailListRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ImportExaminationDetailsRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ImportParticipantsRequest;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailExportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailImportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailRowResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ExportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetExaminationSummaryUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ImportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListExaminationDetailsUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Examination detail endpoints of one health examination batch: the Participant × service matrix,
 * its counters, the Excel export and the Excel reconciliation import.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(
    "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/examination-details")
public class ExaminationDetailController {
  private final ListExaminationDetailsUseCase listUseCase;
  private final GetExaminationSummaryUseCase summaryUseCase;
  private final ExportExaminationDetailsUseCase exportUseCase;
  private final ImportExaminationDetailsUseCase importUseCase;

  /**
   * Lists the examination detail rows of a batch.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param request filters, sorting and pagination
   * @param principal authenticated staff principal
   * @return the requested page; the identification number is masked
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ExaminationDetailRowResponse>>> list(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @Valid @ModelAttribute ExaminationDetailListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "List examination details request: organizationId={}, batchId={}, page={}, size={}",
        organizationId,
        batchId,
        request.getPage(),
        request.getSize());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                listUseCase.execute(organizationId, batchId, request.toQuery(), principal)));
  }

  /**
   * Returns the counters of the examination detail screen.
   *
   * @return 200 with the active roster counters
   */
  @GetMapping("/summary")
  public ResponseEntity<ApiResponse<ExaminationSummaryResponse>> summary(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Examination summary request: organizationId={}, batchId={}", organizationId, batchId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(), summaryUseCase.execute(organizationId, batchId, principal)));
  }

  /**
   * Downloads the examination detail workbook, which is also the import template.
   *
   * @return the XLSX bytes as an attachment
   */
  @GetMapping("/export")
  public ResponseEntity<byte[]> export(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Export examination details request: organizationId={}, batchId={}",
        organizationId,
        batchId);
    ExaminationDetailExportResponse file = exportUseCase.execute(organizationId, batchId, principal);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(ImportParticipantsRequest.XLSX_CONTENT_TYPE))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(file.content());
  }

  /**
   * Reconciles the performed services from an uploaded workbook, all or nothing.
   *
   * @param file the {@code .xlsx} workbook exported from this batch
   * @param idempotencyKey client-generated key; retry the same request with the same key
   * @return 201 with the safe import result; a replayed request returns the stored result
   */
  @PostMapping(path = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<ExaminationDetailImportResponse>> importDetails(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @RequestParam("file") MultipartFile file,
      @RequestHeader("Idempotency-Key") UUID idempotencyKey,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Import examination details request: organizationId={}, batchId={}",
        organizationId,
        batchId);
    ExaminationDetailImportResponse response =
        importUseCase.execute(
            organizationId,
            batchId,
            new ImportExaminationDetailsRequest(file, idempotencyKey).toCommand(),
            principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response));
  }
}
