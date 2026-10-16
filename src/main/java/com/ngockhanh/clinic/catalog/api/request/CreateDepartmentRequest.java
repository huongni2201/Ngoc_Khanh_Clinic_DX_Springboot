package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.CreateDepartmentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
    @NotBlank(message = "Department code is required")
        @Size(max = 50, message = "Department code must not exceed 50 characters")
        String code,
    @NotBlank(message = "Department name is required")
        @Size(max = 200, message = "Department name must not exceed 200 characters")
        String name,
    @NotBlank(message = "Department type is required") String departmentType) {

  public CreateDepartmentCommand toCommand() {
    return new CreateDepartmentCommand(code, name, departmentType);
  }
}
