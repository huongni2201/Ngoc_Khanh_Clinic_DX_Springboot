package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.UpdateMedicineCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateMedicineRequest(
    @NotBlank(message = "Generic name is required")
        @Size(max = 300, message = "Generic name must not exceed 300 characters")
        String genericName,
    @Size(max = 300, message = "Brand name must not exceed 300 characters") String brandName,
    @NotBlank(message = "Strength is required")
        @Size(max = 100, message = "Strength must not exceed 100 characters")
        String strength,
    @NotBlank(message = "Dosage form is required")
        @Size(max = 100, message = "Dosage form must not exceed 100 characters")
        String dosageForm,
    @NotBlank(message = "Unit is required")
        @Size(max = 50, message = "Unit must not exceed 50 characters")
        String unit,
    @NotNull(message = "Row version is required")
        @Min(value = 0, message = "Row version must be non-negative")
        Long rowVersion) {

  public UpdateMedicineCommand toCommand(UUID id) {
    return new UpdateMedicineCommand(
        id, genericName, brandName, strength, dosageForm, unit, rowVersion);
  }
}
