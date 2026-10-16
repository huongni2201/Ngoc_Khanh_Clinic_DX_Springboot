package com.ngockhanh.clinic.catalog.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.api.request.CreateDepartmentRequest;
import com.ngockhanh.clinic.catalog.api.request.DepartmentListRequest;
import com.ngockhanh.clinic.catalog.api.request.ReactivateDepartmentRequest;
import com.ngockhanh.clinic.catalog.api.request.UpdateDepartmentRequest;
import com.ngockhanh.clinic.catalog.application.command.DeactivateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.response.DepartmentItemResponse;
import com.ngockhanh.clinic.catalog.application.response.DepartmentResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateDepartmentUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateDepartmentUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetDepartmentUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListDepartmentsUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateDepartmentUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateDepartmentUseCase;
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
@RequestMapping("/api/v1/catalog/management/departments")
@RequiredArgsConstructor
public class DepartmentManagementController {
  private final ListDepartmentsUseCase listDepartmentsUseCase;
  private final GetDepartmentUseCase getDepartmentUseCase;
  private final CreateDepartmentUseCase createDepartmentUseCase;
  private final UpdateDepartmentUseCase updateDepartmentUseCase;
  private final DeactivateDepartmentUseCase deactivateDepartmentUseCase;
  private final ReactivateDepartmentUseCase reactivateDepartmentUseCase;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<DepartmentItemResponse>>> list(
      @Valid @ModelAttribute DepartmentListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    PageResponse<DepartmentItemResponse> page =
        listDepartmentsUseCase.execute(request.toQuery(), principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), page));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<DepartmentResponse>> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
    DepartmentResponse response = getDepartmentUseCase.execute(id, principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), response));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<DepartmentResponse>> create(
      @Valid @RequestBody CreateDepartmentRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    DepartmentResponse response = createDepartmentUseCase.execute(request.toCommand(), principal);
    URI location = URI.create("/api/v1/catalog/management/departments/" + response.id());
    return ResponseEntity.created(location)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), "Department created successfully", response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<DepartmentResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateDepartmentRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    DepartmentResponse response = updateDepartmentUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Department updated successfully", response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(
      @PathVariable UUID id,
      @RequestParam
          @NotNull(message = "rowVersion is required")
          @Min(value = 0, message = "rowVersion must be non-negative")
          Long rowVersion,
      @AuthenticationPrincipal UserPrincipal principal) {
    deactivateDepartmentUseCase.execute(new DeactivateDepartmentCommand(id, rowVersion), principal);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reactivate")
  public ResponseEntity<ApiResponse<DepartmentResponse>> reactivate(
      @PathVariable UUID id,
      @Valid @RequestBody ReactivateDepartmentRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    DepartmentResponse response =
        reactivateDepartmentUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), "Department reactivated successfully", response));
  }
}
