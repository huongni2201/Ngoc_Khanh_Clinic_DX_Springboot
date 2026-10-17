package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.api.request.ExaminationDetailListRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ImportExaminationDetailsRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ImportParticipantsRequest;
import com.ngockhanh.clinic.healthexamination.api.request.RecordExaminationDetailRequest;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailExportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailImportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailRowResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ExportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetExaminationDetailImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetExaminationSummaryUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ImportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.RecordExaminationDetailUseCase;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Examination detail endpoints of one health examination agreement: the Participant × service
 * matrix, its counters, the Excel export, the import template, the Excel reconciliation import and
 * the manual entry for one Participant.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(
    "/api/v1/organizations/{organizationId}/agreements/{agreementId}/examination-details")
public class ExaminationDetailController {
  private final ListExaminationDetailsUseCase listUseCase;
  private final GetExaminationSummaryUseCase summaryUseCase;
  private final ExportExaminationDetailsUseCase exportUseCase;
  private final ImportExaminationDetailsUseCase importUseCase;
  private final GetExaminationDetailImportTemplateUseCase templateUseCase;
  private final RecordExaminationDetailUseCase recordUseCase;

  /**
   * Lists the examination detail rows of an agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param request filters, sorting and pagination
   * @param principal authenticated staff principal
   * @return the requested page; the identification number is masked
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ExaminationDetailRowResponse>>> list(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @Valid @ModelAttribute ExaminationDetailListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "List examination details request: organizationId={}, agreementId={}, page={}, size={}",
        organizationId,
        agreementId,
        request.getPage(),
        request.getSize());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                listUseCase.execute(organizationId, agreementId, request.toQuery(), principal)));
  }

  /**
   * Returns the counters of the examination detail screen.
   *
   * @return 200 with the active roster counters
   */
  @GetMapping("/summary")
  public ResponseEntity<ApiResponse<ExaminationSummaryResponse>> summary(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Examination summary request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                summaryUseCase.execute(organizationId, agreementId, principal)));
  }

  /**
   * Downloads the examination detail workbook, which is also the import template.
   *
   * @return the XLSX bytes as an attachment
   */
  @GetMapping("/export")
  public ResponseEntity<byte[]> export(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Export examination details request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    ExaminationDetailExportResponse file =
        exportUseCase.execute(organizationId, agreementId, principal);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(ImportParticipantsRequest.XLSX_CONTENT_TYPE))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(file.content());
  }

  /**
   * Downloads the template of the import: the same workbook as the export, named as a template,
   * under its own permission.
   *
   * @return the XLSX bytes as an attachment
   */
  @GetMapping("/import-template")
  public ResponseEntity<byte[]> importTemplate(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Examination detail import template request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    ExaminationDetailExportResponse file =
        templateUseCase.execute(organizationId, agreementId, principal);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(ImportParticipantsRequest.XLSX_CONTENT_TYPE))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(file.content());
  }

  /**
   * Records the examination details of one Participant by hand: attended with the services
   * performed, or absent. A retry of a request that already succeeded returns the stored row.
   *
   * @param participantId Participant identifier
   * @param request attendance, actual date, performed services and the row version the client read
   * @return 200 with the Participant row as stored afterwards
   */
  @PutMapping("/{participantId}")
  public ResponseEntity<ApiResponse<ExaminationDetailRowResponse>> record(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @PathVariable UUID participantId,
      @Valid @RequestBody RecordExaminationDetailRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Record examination detail request: organizationId={}, agreementId={}, participantId={}",
        organizationId,
        agreementId,
        participantId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                recordUseCase.execute(
                    organizationId, agreementId, participantId, request.toCommand(), principal)));
  }

  /**
   * Reconciles the performed services from an uploaded workbook, all or nothing.
   *
   * @param file the {@code .xlsx} workbook exported from this agreement
   * @param idempotencyKey client-generated key; retry the same request with the same key
   * @return 201 with the safe import result; a replayed request returns the stored result
   */
  @PostMapping(path = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<ExaminationDetailImportResponse>> importDetails(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @RequestParam("file") MultipartFile file,
      @RequestHeader("Idempotency-Key") UUID idempotencyKey,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Import examination details request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    ExaminationDetailImportResponse response =
        importUseCase.execute(
            organizationId,
            agreementId,
            new ImportExaminationDetailsRequest(file, idempotencyKey).toCommand(),
            principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response));
  }
}
