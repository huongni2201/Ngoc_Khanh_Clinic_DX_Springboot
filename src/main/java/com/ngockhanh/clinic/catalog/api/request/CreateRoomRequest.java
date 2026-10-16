package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.CreateRoomCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateRoomRequest(
    @NotBlank(message = "Room code is required")
        @Size(max = 50, message = "Room code must not exceed 50 characters")
        String code,
    @NotBlank(message = "Room name is required")
        @Size(max = 200, message = "Room name must not exceed 200 characters")
        String name,
    @NotNull(message = "Department ID is required") UUID departmentId,
    @NotBlank(message = "Room type is required")
        @Size(max = 50, message = "Room type must not exceed 50 characters")
        String roomType) {

  public CreateRoomCommand toCommand() {
    return new CreateRoomCommand(code, name, departmentId, roomType);
  }
}
