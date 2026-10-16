package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.UpdateServiceCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record UpdateServiceRequest(
    @NotBlank(message = "Service name is required")
        @Size(max = 300, message = "Service name must not exceed 300 characters")
        String name,
    @NotBlank(message = "Service type is required") String serviceType,
    UUID specialtyId,
    @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.00", message = "Unit price must not be negative")
        @Digits(
            integer = 12,
            fraction = 2,
            message = "Unit price must have at most 12 integer and 2 decimal digits")
        BigDecimal unitPrice,
    @Size(max = 4000, message = "Preparation instruction must not exceed 4000 characters")
        String preparationInstruction,
    @NotNull(message = "Row version is required")
        @Min(value = 0, message = "Row version must be non-negative")
        Long rowVersion) {

  public UpdateServiceCommand toCommand(UUID id) {
    return new UpdateServiceCommand(
        id, name, serviceType, specialtyId, unitPrice, preparationInstruction, rowVersion);
  }
}
