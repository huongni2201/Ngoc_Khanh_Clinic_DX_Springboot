package com.ngockhanh.clinic.catalog.application.command;

public record CreateMedicineCommand(
    String code,
    String genericName,
    String brandName,
    String strength,
    String dosageForm,
    String unit) {}
