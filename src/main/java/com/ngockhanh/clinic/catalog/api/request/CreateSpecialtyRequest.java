package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.CreateSpecialtyCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSpecialtyRequest(
    @NotBlank(message = "Specialty code is required")
        @Size(max = 50, message = "Specialty code must not exceed 50 characters")
        String code,
    @NotBlank(message = "Specialty name is required")
        @Size(max = 200, message = "Specialty name must not exceed 200 characters")
        String name) {

  public CreateSpecialtyCommand toCommand() {
    return new CreateSpecialtyCommand(code, name);
  }
}
