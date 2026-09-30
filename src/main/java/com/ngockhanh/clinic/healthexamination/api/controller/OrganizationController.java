package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.healthexamination.api.request.OrganizationListRequest;
import com.ngockhanh.clinic.healthexamination.api.request.OrganizationRequest;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.query.OrganizationListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeactivateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListOrganizationsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  private final GetOrganizationUseCase getOrganizationUseCase;
  private final ListOrganizationsUseCase listOrganizationsUseCase;
  private final UpdateOrganizationUseCase updateOrganizationUseCase;
  private final DeactivateOrganizationUseCase deactivateOrganizationUseCase;

  /**
   * Creates a new organization.
   *
   * @param request organization data submitted by the client
   * @return the created organization
   */
  @PostMapping
  public ResponseEntity<ApiResponse<OrganizationResponse>> create(
      @Valid @RequestBody OrganizationRequest request) {
    log.debug("Create organization request received");
    CreateOrganizationCommand command =
        CreateOrganizationCommand.builder()
            .name(request.name())
            .taxCode(request.taxCode())
            .address(request.address())
            .contactName(request.contactName())
            .contactPhone(request.contactPhone())
            .contactJobTitle(request.contactJobTitle())
            .note(request.note())
            .build();

    OrganizationResponse response = createOrganizationUseCase.execute(command);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Organization created", response));
  }

  /**
   * Lists organizations with keyword search, optional status filtering, sorting, and pagination.
   *
   * @param request search, filter, sorting, and pagination parameters
   * @return the requested organization page
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<OrganizationResponse>>> list(
      @Valid @ModelAttribute OrganizationListRequest request) {
    log.debug("List organizations request: page={}, size={}", request.getPage(), request.getSize());
    OrganizationListQuery query =
        OrganizationListQuery.builder()
            .page(request.getPage())
            .size(request.getSize())
            .searchKey(request.getSearchKey())
            .status(request.getStatus())
            .sortBy(request.getSortBy())
            .sortKey(request.getSortKey())
            .build();

    PageResponse<OrganizationResponse> response = listOrganizationsUseCase.execute(query);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Organizations retrieved", response));
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
   * @param request updated organization data submitted by the client
   * @return the updated organization
   */
  @PutMapping("/{organizationId}")
  public ResponseEntity<ApiResponse<OrganizationResponse>> update(
      @PathVariable UUID organizationId, @Valid @RequestBody OrganizationRequest request) {
    log.debug("Update organization request: organizationId={}", organizationId);

    UpdateOrganizationCommand command =
        UpdateOrganizationCommand.builder()
            .name(request.name())
            .taxCode(request.taxCode())
            .address(request.address())
            .contactName(request.contactName())
            .contactPhone(request.contactPhone())
            .contactJobTitle(request.contactJobTitle())
            .note(request.note())
            .build();

    OrganizationResponse response = updateOrganizationUseCase.execute(organizationId, command);

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Organization updated", response));
  }

  /**
   * Deactivates an organization by its identifier.
   *
   * @param organizationId organization identifier
   * @return an empty successful response
   */
  @DeleteMapping("/{organizationId}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID organizationId) {
    log.debug("Deactivate organization request: organizationId={}", organizationId);

    deactivateOrganizationUseCase.execute(organizationId);
    return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Organization deactivated"));
  }
}
