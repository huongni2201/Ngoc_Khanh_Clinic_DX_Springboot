package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.command.CreateMedicineCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMedicineRequest(
    @NotBlank(message = "Medicine code is required")
        @Size(max = 50, message = "Medicine code must not exceed 50 characters")
        String code,
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
        String unit) {

  public CreateMedicineCommand toCommand() {
    return new CreateMedicineCommand(code, genericName, brandName, strength, dosageForm, unit);
  }
}
