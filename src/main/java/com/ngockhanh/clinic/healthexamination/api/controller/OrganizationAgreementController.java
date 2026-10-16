package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.api.request.ActivateHealthExaminationAgreementRequest;
import com.ngockhanh.clinic.healthexamination.api.request.CreateHealthExaminationAgreementRequest;
import com.ngockhanh.clinic.healthexamination.api.request.DeleteHealthExaminationAgreementRequest;
import com.ngockhanh.clinic.healthexamination.api.request.EndHealthExaminationAgreementRequest;
import com.ngockhanh.clinic.healthexamination.api.request.HealthExaminationAgreementListRequest;
import com.ngockhanh.clinic.healthexamination.api.request.UpdateHealthExaminationAgreementRequest;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationAgreementListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ActivateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.EndHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetHealthExaminationAgreementByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Agreements of an organization: services, negotiated prices and validity. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{organizationId}/agreements")
public class OrganizationAgreementController {
  private final CreateHealthExaminationAgreementUseCase createAgreementUseCase;
  private final GetHealthExaminationAgreementByIdUseCase getAgreementUseCase;
  private final UpdateHealthExaminationAgreementUseCase updateAgreementUseCase;
  private final ListHealthExaminationAgreementUseCase listAgreementUseCase;
  private final DeleteHealthExaminationAgreementUseCase deleteAgreementUseCase;
  private final ActivateHealthExaminationAgreementUseCase activateAgreementUseCase;
  private final EndHealthExaminationAgreementUseCase endAgreementUseCase;

  /**
   * Creates a draft health examination agreement for an organization.
   *
   * @param organizationId owning organization
   * @param request agreement configuration submitted by the client
   * @param principal authenticated staff principal
   * @return the created agreement with HTTP 201
   */
  @PostMapping
  public ResponseEntity<ApiResponse<AgreementDetailResponse>> create(
      @PathVariable UUID organizationId,
      @Valid @RequestBody CreateHealthExaminationAgreementRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Create health examination agreement request: organizationId={}", organizationId);
    AgreementDetailResponse response =
        createAgreementUseCase.execute(
            organizationId,
            CreateHealthExaminationAgreementCommand.builder()
                .configuration(request.toConfiguration())
                .build(),
            principal.userId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), "Health examination agreement created", response));
  }

  /**
   * Retrieves one health examination agreement of an organization.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @return the agreement with its services
   */
  @GetMapping("/{agreementId}")
  public ResponseEntity<ApiResponse<AgreementDetailResponse>> get(
      @PathVariable UUID organizationId, @PathVariable UUID agreementId) {
    log.info(
        "Get health examination agreement request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), getAgreementUseCase.execute(organizationId, agreementId)));
  }

  /**
   * Lists the health examination agreements of an organization.
   *
   * @param organizationId owning organization
   * @param request search and pagination parameters
   * @return the requested page of agreement summaries
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<AgreementSummaryResponse>>> list(
      @PathVariable UUID organizationId,
      @Valid @ModelAttribute HealthExaminationAgreementListRequest request) {
    log.info(
        "List health examination agreements request: organizationId={}, page={}, size={}",
        organizationId,
        request.getPage(),
        request.getSize());
    HealthExaminationAgreementListQuery query =
        HealthExaminationAgreementListQuery.builder()
            .page(request.getPage())
            .size(request.getSize())
            .searchKey(request.getSearchKey())
            .sortKey(request.getSortKey())
            .sortBy(request.getSortBy())
            .build();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), listAgreementUseCase.execute(organizationId, query)));
  }

  /**
   * Replaces the whole configuration of a draft agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param request full configuration and the expected row version
   * @param principal authenticated staff principal
   * @return the updated agreement
   */
  @PutMapping("/{agreementId}")
  public ResponseEntity<ApiResponse<AgreementDetailResponse>> update(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @Valid @RequestBody UpdateHealthExaminationAgreementRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Update health examination agreement request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    AgreementDetailResponse response =
        updateAgreementUseCase.execute(
            organizationId,
            agreementId,
            UpdateHealthExaminationAgreementCommand.builder()
                .configuration(request.toConfiguration())
                .rowVersion(request.rowVersion())
                .build(),
            principal.userId());
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), "Health examination agreement updated", response));
  }

  /**
   * Activates a draft agreement (DRAFT to ACTIVE). The services are fixed from then on, while
   * Participants and sessions can still be managed.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param request carries the expected row version
   * @param principal authenticated staff principal
   * @return the activated agreement
   */
  @PostMapping("/{agreementId}/activate")
  public ResponseEntity<ApiResponse<AgreementDetailResponse>> activate(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @Valid @RequestBody ActivateHealthExaminationAgreementRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Activate health examination agreement request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    AgreementDetailResponse response =
        activateAgreementUseCase.execute(
            organizationId, agreementId, request.toCommand(), principal.userId());
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), "Health examination agreement activated", response));
  }

  /**
   * Ends an active agreement (ACTIVE to ENDED). Its history stays readable and nothing more
   * changes.
   *
   * @return the ended agreement
   */
  @PostMapping("/{agreementId}/end")
  public ResponseEntity<ApiResponse<AgreementDetailResponse>> end(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @Valid @RequestBody EndHealthExaminationAgreementRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "End health examination agreement request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    AgreementDetailResponse response =
        endAgreementUseCase.execute(
            organizationId, agreementId, request.toCommand(), principal.userId());
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Health examination agreement ended", response));
  }

  /**
   * Soft-deletes a draft agreement that has no Participant, no session and no integration history.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param request carries the expected row version
   * @param principal authenticated staff principal
   * @return an empty 204 response
   */
  @DeleteMapping("/{agreementId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @Valid @ModelAttribute DeleteHealthExaminationAgreementRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Delete health examination agreement request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    deleteAgreementUseCase.execute(
        organizationId,
        agreementId,
        DeleteHealthExaminationAgreementCommand.builder().rowVersion(request.rowVersion()).build(),
        principal.userId());
    return ResponseEntity.noContent().build();
  }
}
