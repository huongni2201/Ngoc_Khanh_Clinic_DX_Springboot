package com.ngockhanh.clinic.catalog.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.api.request.CreateSpecialtyRequest;
import com.ngockhanh.clinic.catalog.api.request.ReactivateSpecialtyRequest;
import com.ngockhanh.clinic.catalog.api.request.SpecialtyListRequest;
import com.ngockhanh.clinic.catalog.api.request.UpdateSpecialtyRequest;
import com.ngockhanh.clinic.catalog.application.command.DeactivateSpecialtyCommand;
import com.ngockhanh.clinic.catalog.application.response.SpecialtyItemResponse;
import com.ngockhanh.clinic.catalog.application.response.SpecialtyResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateSpecialtyUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateSpecialtyUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetSpecialtyUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListSpecialtiesUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateSpecialtyUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateSpecialtyUseCase;
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
@RequestMapping("/api/v1/catalog/management/specialties")
@RequiredArgsConstructor
public class SpecialtyManagementController {
  private final ListSpecialtiesUseCase listSpecialtiesUseCase;
  private final GetSpecialtyUseCase getSpecialtyUseCase;
  private final CreateSpecialtyUseCase createSpecialtyUseCase;
  private final UpdateSpecialtyUseCase updateSpecialtyUseCase;
  private final DeactivateSpecialtyUseCase deactivateSpecialtyUseCase;
  private final ReactivateSpecialtyUseCase reactivateSpecialtyUseCase;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<SpecialtyItemResponse>>> list(
      @Valid @ModelAttribute SpecialtyListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    PageResponse<SpecialtyItemResponse> page =
        listSpecialtiesUseCase.execute(request.toQuery(), principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), page));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<SpecialtyResponse>> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
    SpecialtyResponse response = getSpecialtyUseCase.execute(id, principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), response));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<SpecialtyResponse>> create(
      @Valid @RequestBody CreateSpecialtyRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    SpecialtyResponse response = createSpecialtyUseCase.execute(request.toCommand(), principal);
    URI location = URI.create("/api/v1/catalog/management/specialties/" + response.id());
    return ResponseEntity.created(location)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), "Specialty created successfully", response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<SpecialtyResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateSpecialtyRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    SpecialtyResponse response = updateSpecialtyUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Specialty updated successfully", response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(
      @PathVariable UUID id,
      @RequestParam
          @NotNull(message = "rowVersion is required")
          @Min(value = 0, message = "rowVersion must be non-negative")
          Long rowVersion,
      @AuthenticationPrincipal UserPrincipal principal) {
    deactivateSpecialtyUseCase.execute(new DeactivateSpecialtyCommand(id, rowVersion), principal);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reactivate")
  public ResponseEntity<ApiResponse<SpecialtyResponse>> reactivate(
      @PathVariable UUID id,
      @Valid @RequestBody ReactivateSpecialtyRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    SpecialtyResponse response =
        reactivateSpecialtyUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Specialty reactivated successfully", response));
  }
}
