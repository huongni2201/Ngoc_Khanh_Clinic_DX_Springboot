package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.ReactivateMedicineCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReactivateMedicineRequest(
    @NotNull(message = "Row version is required")
        @Min(value = 0, message = "Row version must be non-negative")
        Long rowVersion) {

  public ReactivateMedicineCommand toCommand(UUID id) {
    return new ReactivateMedicineCommand(id, rowVersion);
  }
}
