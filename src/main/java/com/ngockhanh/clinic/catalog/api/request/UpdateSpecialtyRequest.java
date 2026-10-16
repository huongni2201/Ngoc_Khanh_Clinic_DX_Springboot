package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.UpdateSpecialtyCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateSpecialtyRequest(
    @NotBlank(message = "Specialty name is required")
        @Size(max = 200, message = "Specialty name must not exceed 200 characters")
        String name,
    @NotNull(message = "Row version is required")
        @Min(value = 0, message = "Row version must be non-negative")
        Long rowVersion) {

  public UpdateSpecialtyCommand toCommand(UUID id) {
    return new UpdateSpecialtyCommand(id, name, rowVersion);
  }
}
