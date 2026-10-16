package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record ReactivateMedicineCommand(UUID id, long rowVersion) {}
