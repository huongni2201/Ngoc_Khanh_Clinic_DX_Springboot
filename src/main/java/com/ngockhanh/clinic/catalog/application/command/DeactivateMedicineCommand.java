package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record DeactivateMedicineCommand(UUID id, long rowVersion) {}
