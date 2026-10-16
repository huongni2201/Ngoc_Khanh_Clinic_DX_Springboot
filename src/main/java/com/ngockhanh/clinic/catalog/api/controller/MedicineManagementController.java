package com.ngockhanh.clinic.catalog.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.api.request.CreateMedicineRequest;
import com.ngockhanh.clinic.catalog.api.request.MedicineListRequest;
import com.ngockhanh.clinic.catalog.api.request.ReactivateMedicineRequest;
import com.ngockhanh.clinic.catalog.api.request.UpdateMedicineRequest;
import com.ngockhanh.clinic.catalog.application.command.DeactivateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.response.MedicineItemResponse;
import com.ngockhanh.clinic.catalog.application.response.MedicineResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateMedicineUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateMedicineUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetMedicineUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListMedicinesUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateMedicineUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateMedicineUseCase;
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
@RequestMapping("/api/v1/catalog/management/medicines")
@RequiredArgsConstructor
public class MedicineManagementController {
  private final ListMedicinesUseCase listMedicinesUseCase;
  private final GetMedicineUseCase getMedicineUseCase;
  private final CreateMedicineUseCase createMedicineUseCase;
  private final UpdateMedicineUseCase updateMedicineUseCase;
  private final DeactivateMedicineUseCase deactivateMedicineUseCase;
  private final ReactivateMedicineUseCase reactivateMedicineUseCase;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<MedicineItemResponse>>> list(
      @Valid @ModelAttribute MedicineListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    PageResponse<MedicineItemResponse> page =
        listMedicinesUseCase.execute(request.toQuery(), principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), page));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<MedicineResponse>> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
    MedicineResponse response = getMedicineUseCase.execute(id, principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), response));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<MedicineResponse>> create(
      @Valid @RequestBody CreateMedicineRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    MedicineResponse response = createMedicineUseCase.execute(request.toCommand(), principal);
    URI location = URI.create("/api/v1/catalog/management/medicines/" + response.id());
    return ResponseEntity.created(location)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), "Medicine created successfully", response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<MedicineResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateMedicineRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    MedicineResponse response = updateMedicineUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Medicine updated successfully", response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(
      @PathVariable UUID id,
      @RequestParam
          @NotNull(message = "rowVersion is required")
          @Min(value = 0, message = "rowVersion must be non-negative")
          Long rowVersion,
      @AuthenticationPrincipal UserPrincipal principal) {
    deactivateMedicineUseCase.execute(new DeactivateMedicineCommand(id, rowVersion), principal);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reactivate")
  public ResponseEntity<ApiResponse<MedicineResponse>> reactivate(
      @PathVariable UUID id,
      @Valid @RequestBody ReactivateMedicineRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    MedicineResponse response = reactivateMedicineUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Medicine reactivated successfully", response));
  }
}
