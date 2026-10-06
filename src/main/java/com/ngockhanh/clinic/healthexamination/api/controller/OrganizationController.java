package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.healthexamination.api.request.CreateOrganizationRequest;
import com.ngockhanh.clinic.healthexamination.api.request.DeleteOrganizationRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ListOrganizationRequest;
import com.ngockhanh.clinic.healthexamination.api.request.UpdateOrganizationRequest;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.ListOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetOrganizationByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase;
import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

  private final CreateOrganizationUseCase createOrganizationUseCase;
  private final GetOrganizationByIdUseCase getOrganizationUseCase;
  private final UpdateOrganizationUseCase updateOrganizationUseCase;
  private final ListOrganizationUseCase listOrganizationUseCase;
  private final DeleteOrganizationUseCase deleteOrganizationUseCase;

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
            .name(request.name())
            .taxCode(request.taxCode())
            .phone(request.phone())
            .email(request.email())
            .address(request.address())
            .contactFullName(request.contactFullName())
            .contactPhone(request.contactPhone())
            .contactEmail(request.contactEmail())
            .build();

    OrganizationResponse response = createOrganizationUseCase.execute(command, principal.userId());

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Organization created", response));
  }

  /**
   * Lists active organizations using keyword search, allowlisted sorting and one-based pagination.
   *
   * @param request search and pagination parameters
   * @return the requested page of organizations
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<OrganizationResponse>>> list(
      @Valid @ModelAttribute ListOrganizationRequest request) {
    log.debug("List organization request: page={}, size={}", request.getPage(), request.getSize());
    ListOrganizationCommand command =
        ListOrganizationCommand.builder()
            .page(request.getPage())
            .size(request.getSize())
            .searchKey(request.getSearchKey())
            .sortKey(request.getSortKey())
            .sortBy(request.getSortBy())
            .build();

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), listOrganizationUseCase.execute(command)));
  }

  /**
   * Retrieves an organization by its identifier.
   *
   * @param organizationId organization identifier
   * @return the requested organization
   */
  @GetMapping("/{organizationId}")
  public ResponseEntity<ApiResponse<OrganizationResponse>> get(@PathVariable UUID organizationId) {

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
            .name(request.name())
            .taxCode(request.taxCode())
            .phone(request.phone())
            .email(request.email())
            .address(request.address())
            .contactFullName(request.contactFullName())
            .contactPhone(request.contactPhone())
            .contactEmail(request.contactEmail())
            .rowVersion(request.rowVersion())
            .build();

    OrganizationResponse response =
        updateOrganizationUseCase.execute(organizationId, command, principal.userId());

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Organization updated", response));
  }

  /**
   * Deactivates an organization. The row and its batch history are kept.
   *
   * @param organizationId organization identifier
   * @param request carries the expected row version
   * @param principal authenticated staff principal
   * @return an empty 204 response
   */
  @DeleteMapping("/{organizationId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID organizationId,
      @Valid @ModelAttribute DeleteOrganizationRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug("Delete organization request: organizationId={}", organizationId);

    deleteOrganizationUseCase.execute(
        organizationId,
        DeleteOrganizationCommand.builder().rowVersion(request.rowVersion()).build(),
        principal.userId());

    return ResponseEntity.noContent().build();
  }
}
