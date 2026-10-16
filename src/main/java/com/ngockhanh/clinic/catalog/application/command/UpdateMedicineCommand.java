package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record UpdateMedicineCommand(
    UUID id,
    String genericName,
    String brandName,
    String strength,
    String dosageForm,
    String unit,
    long rowVersion) {}
