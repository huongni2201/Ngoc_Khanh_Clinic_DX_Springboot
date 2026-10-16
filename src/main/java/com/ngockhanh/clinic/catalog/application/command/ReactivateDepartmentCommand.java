package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record ReactivateDepartmentCommand(UUID id, long rowVersion) {}
