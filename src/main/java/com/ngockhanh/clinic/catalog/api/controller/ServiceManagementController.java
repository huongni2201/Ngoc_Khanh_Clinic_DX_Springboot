package com.ngockhanh.clinic.catalog.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.api.request.CreateServiceRequest;
import com.ngockhanh.clinic.catalog.api.request.ManagedServiceListRequest;
import com.ngockhanh.clinic.catalog.api.request.ReactivateServiceRequest;
import com.ngockhanh.clinic.catalog.api.request.UpdateServiceRequest;
import com.ngockhanh.clinic.catalog.application.command.DeactivateServiceCommand;
import com.ngockhanh.clinic.catalog.application.response.ManagedServiceItemResponse;
import com.ngockhanh.clinic.catalog.application.response.ManagedServiceResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateManagedServiceUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateManagedServiceUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetManagedServiceUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListManagedServicesUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateManagedServiceUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateManagedServiceUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/catalog/management/services")
@RequiredArgsConstructor
public class ServiceManagementController {
  private final ListManagedServicesUseCase listManagedServicesUseCase;
  private final GetManagedServiceUseCase getManagedServiceUseCase;
  private final CreateManagedServiceUseCase createManagedServiceUseCase;
  private final UpdateManagedServiceUseCase updateManagedServiceUseCase;
  private final DeactivateManagedServiceUseCase deactivateManagedServiceUseCase;
  private final ReactivateManagedServiceUseCase reactivateManagedServiceUseCase;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ManagedServiceItemResponse>>> list(
      @Valid @ModelAttribute ManagedServiceListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    PageResponse<ManagedServiceItemResponse> page =
        listManagedServicesUseCase.execute(request.toQuery(), principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), page));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ManagedServiceResponse>> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
    ManagedServiceResponse response = getManagedServiceUseCase.execute(id, principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), response));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ManagedServiceResponse>> create(
      @Valid @RequestBody CreateServiceRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    ManagedServiceResponse response =
        createManagedServiceUseCase.execute(request.toCommand(), principal);
    URI location = URI.create("/api/v1/catalog/management/services/" + response.id());
    return ResponseEntity.created(location)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), "Service created successfully", response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<ManagedServiceResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateServiceRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    ManagedServiceResponse response =
        updateManagedServiceUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Service updated successfully", response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(
      @PathVariable UUID id,
      @RequestParam
          @NotNull(message = "rowVersion is required")
          @Min(value = 0, message = "rowVersion must be non-negative")
          Long rowVersion,
      @AuthenticationPrincipal UserPrincipal principal) {
    deactivateManagedServiceUseCase.execute(
        new DeactivateServiceCommand(id, rowVersion), principal);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reactivate")
  public ResponseEntity<ApiResponse<ManagedServiceResponse>> reactivate(
      @PathVariable UUID id,
      @Valid @RequestBody ReactivateServiceRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    ManagedServiceResponse response =
        reactivateManagedServiceUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Service reactivated successfully", response));
  }
}
