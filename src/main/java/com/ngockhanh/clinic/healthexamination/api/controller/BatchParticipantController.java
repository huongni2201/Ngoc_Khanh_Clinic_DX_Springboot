package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.api.request.ImportParticipantsRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ParticipantListRequest;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportTemplateResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ImportParticipantsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListParticipantsUseCase;
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

/** Participant roster endpoints of one health examination batch: list, template and Excel import. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(
    "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants")
public class BatchParticipantController {
  private final ListParticipantsUseCase listParticipantsUseCase;
  private final GetParticipantImportTemplateUseCase templateUseCase;
  private final ImportParticipantsUseCase importUseCase;

  /**
   * Lists the Participants of a batch.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param request filters, sorting and pagination
   * @param principal authenticated staff principal
   * @return the requested page; the identification number is masked
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ParticipantSummaryResponse>>> list(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @Valid @ModelAttribute ParticipantListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "List participants request: organizationId={}, batchId={}, page={}, size={}",
        organizationId,
        batchId,
        request.getPage(),
        request.getSize());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                listParticipantsUseCase.execute(
                    organizationId, batchId, request.toQuery(), principal)));
  }

  /**
   * Downloads the Excel import template of a batch.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param principal authenticated staff principal
   * @return the XLSX bytes as an attachment
   */
  @GetMapping("/import-template")
  public ResponseEntity<byte[]> downloadTemplate(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Participant import template request: organizationId={}, batchId={}",
        organizationId,
        batchId);
    ParticipantImportTemplateResponse template =
        templateUseCase.execute(organizationId, batchId, principal);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(ImportParticipantsRequest.XLSX_CONTENT_TYPE))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(template.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(template.content());
  }

  /**
   * Adds the Participants of an uploaded workbook to the batch roster, all or nothing.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param file the {@code .xlsx} workbook
   * @param rowVersion batch configuration version the workbook was prepared for
   * @param idempotencyKey client-generated key; retry the same request with the same key
   * @param principal authenticated staff principal
   * @return 201 with the safe import result; a replayed request returns the stored result
   */
  @PostMapping(path = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<ParticipantImportResponse>> importParticipants(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "rowVersion", required = false) Long rowVersion,
      @RequestHeader("Idempotency-Key") UUID idempotencyKey,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Import participants request: organizationId={}, batchId={}", organizationId, batchId);
    ParticipantImportResponse response =
        importUseCase.execute(
            organizationId,
            batchId,
            new ImportParticipantsRequest(file, rowVersion, idempotencyKey).toCommand(),
            principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response));
  }
}
