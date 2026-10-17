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
 * Participant roster endpoints of one health examination agreement: list, template, Excel import
 * and manual add, detail, edit, cancel and reactivate of a single Participant.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{organizationId}/agreements/{agreementId}/participants")
public class AgreementParticipantController {
  private final ListParticipantsUseCase listParticipantsUseCase;
  private final GetParticipantImportTemplateUseCase templateUseCase;
  private final ImportParticipantsUseCase importUseCase;
  private final CreateParticipantUseCase createUseCase;
  private final GetParticipantDetailUseCase detailUseCase;
  private final UpdateParticipantUseCase updateUseCase;
  private final CancelParticipantUseCase cancelUseCase;
  private final ReactivateParticipantUseCase reactivateUseCase;

  /**
   * Lists the Participants of an agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param request filters, sorting and pagination
   * @param principal authenticated staff principal
   * @return the requested page; the identification number is masked
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ParticipantSummaryResponse>>> list(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @Valid @ModelAttribute ParticipantListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "List participants request: organizationId={}, agreementId={}, page={}, size={}",
        organizationId,
        agreementId,
        request.getPage(),
        request.getSize());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                listParticipantsUseCase.execute(
                    organizationId, agreementId, request.toQuery(), principal)));
  }

  /**
   * Downloads the Excel import template of an agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param principal authenticated staff principal
   * @return the XLSX bytes as an attachment
   */
  @GetMapping("/import-template")
  public ResponseEntity<byte[]> downloadTemplate(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Participant import template request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    ParticipantImportTemplateResponse template =
        templateUseCase.execute(organizationId, agreementId, principal);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(ImportParticipantsRequest.XLSX_CONTENT_TYPE))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(template.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(template.content());
  }

  /**
   * Adds the Participants of an uploaded workbook to the agreement roster, all or nothing.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param file the {@code .xlsx} workbook
   * @param rowVersion agreement configuration version the workbook was prepared for
   * @param idempotencyKey client-generated key; retry the same request with the same key
   * @param sessionId optional open session to schedule every imported Participant into
   * @param principal authenticated staff principal
   * @return 201 with the safe import result; a replayed request returns the stored result
   */
  @PostMapping(path = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<ParticipantImportResponse>> importParticipants(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "rowVersion", required = false) Long rowVersion,
      @RequestHeader("Idempotency-Key") UUID idempotencyKey,
      @RequestParam(value = "sessionId", required = false) UUID sessionId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Import participants request: organizationId={}, agreementId={}, sessionId={}",
        organizationId,
        agreementId,
        sessionId);
    ParticipantImportResponse response =
        importUseCase.execute(
            organizationId,
            agreementId,
            new ImportParticipantsRequest(file, rowVersion, idempotencyKey, sessionId).toCommand(),
            principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response));
  }

  /**
   * Adds one Participant to the agreement by hand.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param request roster fields
   * @param principal authenticated staff principal
   * @return 201 with the stored Participant in full
   */
  @PostMapping
  public ResponseEntity<ApiResponse<ParticipantDetailResponse>> create(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @Valid @RequestBody CreateParticipantRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Create participant request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    ParticipantDetailResponse response =
        createUseCase.execute(organizationId, agreementId, request.toCommand(), principal);
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
      @PathVariable UUID agreementId,
      @PathVariable UUID participantId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Participant detail request: organizationId={}, agreementId={}, participantId={}",
        organizationId,
        agreementId,
        participantId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                detailUseCase.execute(organizationId, agreementId, participantId, principal)));
  }

  /**
   * Replaces the roster fields of one Participant.
   *
   * @return 200 with the stored Participant and its new row version
   */
  @PutMapping("/{participantId}")
  public ResponseEntity<ApiResponse<ParticipantDetailResponse>> update(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @PathVariable UUID participantId,
      @Valid @RequestBody UpdateParticipantRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Update participant request: organizationId={}, agreementId={}, participantId={}",
        organizationId,
        agreementId,
        participantId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                updateUseCase.execute(
                    organizationId, agreementId, participantId, request.toCommand(), principal)));
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
      @PathVariable UUID agreementId,
      @PathVariable UUID participantId,
      @Valid @ModelAttribute CancelParticipantRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Cancel participant request: organizationId={}, agreementId={}, participantId={}",
        organizationId,
        agreementId,
        participantId);
    cancelUseCase.execute(
        organizationId, agreementId, participantId, request.toCommand(), principal);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  /**
   * Returns a cancelled Participant to the active roster. The same row becomes {@code ACTIVE}
   * again; nothing is inserted.
   *
   * @param request expected row version
   * @return 200 with the stored Participant and its new row version
   */
  @PostMapping("/{participantId}/reactivate")
  public ResponseEntity<ApiResponse<ParticipantDetailResponse>> reactivate(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @PathVariable UUID participantId,
      @Valid @RequestBody ReactivateParticipantRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Reactivate participant request: organizationId={}, agreementId={}, participantId={}",
        organizationId,
        agreementId,
        participantId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                reactivateUseCase.execute(
                    organizationId, agreementId, participantId, request.toCommand(), principal)));
  }
}
