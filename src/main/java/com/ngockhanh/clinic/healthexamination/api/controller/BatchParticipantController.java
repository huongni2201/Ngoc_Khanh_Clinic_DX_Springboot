package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.api.request.CancelParticipantRequest;
import com.ngockhanh.clinic.healthexamination.api.request.CreateParticipantRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ImportParticipantsRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ParticipantListRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ReactivateParticipantRequest;
import com.ngockhanh.clinic.healthexamination.api.request.UpdateParticipantRequest;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportTemplateResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CancelParticipantUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateParticipantUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantDetailUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ImportParticipantsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListParticipantsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ReactivateParticipantUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateParticipantUseCase;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Participant roster endpoints of one health examination batch: list, template, Excel import and
 * manual add, detail, edit, cancel and reactivate of a single Participant.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(
    "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants")
public class BatchParticipantController {
  private final ListParticipantsUseCase listParticipantsUseCase;
  private final GetParticipantImportTemplateUseCase templateUseCase;
  private final ImportParticipantsUseCase importUseCase;
  private final CreateParticipantUseCase createUseCase;
  private final GetParticipantDetailUseCase detailUseCase;
  private final UpdateParticipantUseCase updateUseCase;
  private final CancelParticipantUseCase cancelUseCase;
  private final ReactivateParticipantUseCase reactivateUseCase;

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

  /**
   * Adds one Participant to the batch by hand.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param request roster fields and examination day
   * @param principal authenticated staff principal
   * @return 201 with the stored Participant in full
   */
  @PostMapping
  public ResponseEntity<ApiResponse<ParticipantDetailResponse>> create(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @Valid @RequestBody CreateParticipantRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug("Create participant request: organizationId={}, batchId={}", organizationId, batchId);
    ParticipantDetailResponse response =
        createUseCase.execute(organizationId, batchId, request.toCommand(), principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response));
  }

  /**
   * Reads one Participant in full for the edit form.
   *
   * @return 200 with the complete identification number, phone and email
   */
  @GetMapping("/{participantId}")
  public ResponseEntity<ApiResponse<ParticipantDetailResponse>> detail(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @PathVariable UUID participantId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Participant detail request: organizationId={}, batchId={}, participantId={}",
        organizationId,
        batchId,
        participantId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                detailUseCase.execute(organizationId, batchId, participantId, principal)));
  }

  /**
   * Replaces the roster fields and examination day of one Participant.
   *
   * @return 200 with the stored Participant and its new row version
   */
  @PutMapping("/{participantId}")
  public ResponseEntity<ApiResponse<ParticipantDetailResponse>> update(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @PathVariable UUID participantId,
      @Valid @RequestBody UpdateParticipantRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Update participant request: organizationId={}, batchId={}, participantId={}",
        organizationId,
        batchId,
        participantId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                updateUseCase.execute(
                    organizationId, batchId, participantId, request.toCommand(), principal)));
  }

  /**
   * Cancels one Participant. Nothing is deleted; the roster status becomes {@code CANCELLED}.
   *
   * @param request carries the expected row version as a query parameter
   * @return an empty 204 response
   */
  @DeleteMapping("/{participantId}")
  public ResponseEntity<Void> cancel(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @PathVariable UUID participantId,
      @Valid @ModelAttribute CancelParticipantRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Cancel participant request: organizationId={}, batchId={}, participantId={}",
        organizationId,
        batchId,
        participantId);
    cancelUseCase.execute(organizationId, batchId, participantId, request.toCommand(), principal);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  /**
   * Returns a cancelled Participant to the active roster. The same row becomes {@code ACTIVE}
   * again; nothing is inserted.
   *
   * @param request expected row version and an optional examination day
   * @return 200 with the stored Participant and its new row version
   */
  @PostMapping("/{participantId}/reactivate")
  public ResponseEntity<ApiResponse<ParticipantDetailResponse>> reactivate(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @PathVariable UUID participantId,
      @Valid @RequestBody ReactivateParticipantRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Reactivate participant request: organizationId={}, batchId={}, participantId={}",
        organizationId,
        batchId,
        participantId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                reactivateUseCase.execute(
                    organizationId, batchId, participantId, request.toCommand(), principal)));
  }
}
