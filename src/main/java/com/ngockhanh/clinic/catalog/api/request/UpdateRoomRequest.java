package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.UpdateRoomCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateRoomRequest(
    @NotBlank(message = "Room name is required")
        @Size(max = 200, message = "Room name must not exceed 200 characters")
        String name,
    @NotBlank(message = "Room type is required")
        @Size(max = 50, message = "Room type must not exceed 50 characters")
        String roomType,
    @NotNull(message = "Row version is required")
        @Min(value = 0, message = "Row version must be non-negative")
        Long rowVersion) {

  public UpdateRoomCommand toCommand(UUID id) {
    return new UpdateRoomCommand(id, name, roomType, rowVersion);
  }
}
