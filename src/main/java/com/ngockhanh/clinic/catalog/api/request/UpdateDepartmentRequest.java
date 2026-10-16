package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.UpdateDepartmentCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateDepartmentRequest(
    @NotBlank(message = "Department name is required")
        @Size(max = 200, message = "Department name must not exceed 200 characters")
        String name,
    @NotBlank(message = "Department type is required") String departmentType,
    @NotNull(message = "Row version is required")
        @Min(value = 0, message = "Row version must be non-negative")
        Long rowVersion) {

  public UpdateDepartmentCommand toCommand(UUID id) {
    return new UpdateDepartmentCommand(id, name, departmentType, rowVersion);
  }
}
