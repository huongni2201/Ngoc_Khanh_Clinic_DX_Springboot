package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.CreateServiceCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateServiceRequest(
    @NotBlank(message = "Service code is required")
        @Size(max = 50, message = "Service code must not exceed 50 characters")
        String code,
    @NotBlank(message = "Service name is required")
        @Size(max = 300, message = "Service name must not exceed 300 characters")
        String name,
    @NotBlank(message = "Service type is required") String serviceType,
    @NotNull(message = "Performing department ID is required") UUID performingDepartmentId,
    UUID specialtyId,
    @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.00", message = "Unit price must not be negative")
        @Digits(
            integer = 12,
            fraction = 2,
            message = "Unit price must have at most 12 integer and 2 decimal digits")
        BigDecimal unitPrice,
    @Size(max = 4000, message = "Preparation instruction must not exceed 4000 characters")
        String preparationInstruction) {

  public CreateServiceCommand toCommand() {
    return new CreateServiceCommand(
        code,
        name,
        serviceType,
        performingDepartmentId,
        specialtyId,
        unitPrice,
        preparationInstruction);
  }
}
