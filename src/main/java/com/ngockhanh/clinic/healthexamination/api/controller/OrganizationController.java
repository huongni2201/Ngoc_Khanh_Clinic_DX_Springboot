package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.healthexamination.api.request.CreateOrganizationRequest;
import com.ngockhanh.clinic.healthexamination.api.request.UpdateOrganizationRequest;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetOrganizationByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase;
import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

  private final CreateOrganizationUseCase createOrganizationUseCase;
  private final GetOrganizationByIdUseCase getOrganizationUseCase;
  private final UpdateOrganizationUseCase updateOrganizationUseCase;

  /**
   * Creates a new organization.
   *
   * @param request organization data submitted by the client
   * @param principal authenticated staff principal
   * @return the created organization
   */
  @PostMapping
  public ResponseEntity<ApiResponse<OrganizationResponse>> create(
      @Valid @RequestBody CreateOrganizationRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug("Create organization request received");
    CreateOrganizationCommand command =
        CreateOrganizationCommand.builder()
            .code(request.code())
            .name(request.name())
            .organizationType(request.organizationType())
            .taxCode(request.taxCode())
            .phone(request.phone())
            .email(request.email())
            .address(request.address())
            .contactFullName(request.contactFullName())
            .contactPosition(request.contactPosition())
            .contactPhone(request.contactPhone())
            .contactEmail(request.contactEmail())
            .build();

    OrganizationResponse response = createOrganizationUseCase.execute(command, principal.userId());

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Organization created", response));
  }

  /**
   * Retrieves an organization by its identifier.
   *
   * @param organizationId organization identifier
   * @return the requested organization
   */
  @GetMapping("/{organizationId}")
  public ResponseEntity<ApiResponse<OrganizationResponse>> get(
      @PathVariable UUID organizationId
  ) {

    log.debug("Get organization request: organizationId={}", organizationId);

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), getOrganizationUseCase.execute(organizationId)));
  }

  /**
   * Updates an existing organization.
   *
   * @param organizationId organization identifier
   * @param request updated organization data and expected row version submitted by the client
   * @param principal authenticated staff principal
   * @return the updated organization
   */
  @PutMapping("/{organizationId}")
  public ResponseEntity<ApiResponse<OrganizationResponse>> update(
      @PathVariable UUID organizationId,
      @Valid @RequestBody UpdateOrganizationRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug("Update organization request: organizationId={}", organizationId);

    UpdateOrganizationCommand command =
        UpdateOrganizationCommand.builder()
            .code(request.code())
            .name(request.name())
            .organizationType(request.organizationType())
            .taxCode(request.taxCode())
            .phone(request.phone())
            .email(request.email())
            .address(request.address())
            .contactFullName(request.contactFullName())
            .contactPosition(request.contactPosition())
            .contactPhone(request.contactPhone())
            .contactEmail(request.contactEmail())
            .rowVersion(request.rowVersion())
            .build();

    OrganizationResponse response =
        updateOrganizationUseCase.execute(organizationId, command, principal.userId());

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Organization updated", response));
  }
}
