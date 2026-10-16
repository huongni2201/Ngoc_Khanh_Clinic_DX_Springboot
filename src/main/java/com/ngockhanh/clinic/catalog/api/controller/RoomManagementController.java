package com.ngockhanh.clinic.catalog.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.api.request.CreateRoomRequest;
import com.ngockhanh.clinic.catalog.api.request.ReactivateRoomRequest;
import com.ngockhanh.clinic.catalog.api.request.RoomListRequest;
import com.ngockhanh.clinic.catalog.api.request.UpdateRoomRequest;
import com.ngockhanh.clinic.catalog.application.command.DeactivateRoomCommand;
import com.ngockhanh.clinic.catalog.application.response.RoomItemResponse;
import com.ngockhanh.clinic.catalog.application.response.RoomResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateRoomUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateRoomUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetRoomUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListRoomsUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateRoomUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateRoomUseCase;
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
@RequestMapping("/api/v1/catalog/management/rooms")
@RequiredArgsConstructor
public class RoomManagementController {
  private final ListRoomsUseCase listRoomsUseCase;
  private final GetRoomUseCase getRoomUseCase;
  private final CreateRoomUseCase createRoomUseCase;
  private final UpdateRoomUseCase updateRoomUseCase;
  private final DeactivateRoomUseCase deactivateRoomUseCase;
  private final ReactivateRoomUseCase reactivateRoomUseCase;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<RoomItemResponse>>> list(
      @Valid @ModelAttribute RoomListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    PageResponse<RoomItemResponse> page = listRoomsUseCase.execute(request.toQuery(), principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), page));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<RoomResponse>> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
    RoomResponse response = getRoomUseCase.execute(id, principal);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), response));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<RoomResponse>> create(
      @Valid @RequestBody CreateRoomRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    RoomResponse response = createRoomUseCase.execute(request.toCommand(), principal);
    URI location = URI.create("/api/v1/catalog/management/rooms/" + response.id());
    return ResponseEntity.created(location)
        .body(
            ApiResponse.success(HttpStatus.CREATED.value(), "Room created successfully", response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<RoomResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRoomRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    RoomResponse response = updateRoomUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Room updated successfully", response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(
      @PathVariable UUID id,
      @RequestParam
          @NotNull(message = "rowVersion is required")
          @Min(value = 0, message = "rowVersion must be non-negative")
          Long rowVersion,
      @AuthenticationPrincipal UserPrincipal principal) {
    deactivateRoomUseCase.execute(new DeactivateRoomCommand(id, rowVersion), principal);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reactivate")
  public ResponseEntity<ApiResponse<RoomResponse>> reactivate(
      @PathVariable UUID id,
      @Valid @RequestBody ReactivateRoomRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    RoomResponse response = reactivateRoomUseCase.execute(request.toCommand(id), principal);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Room reactivated successfully", response));
  }
}
