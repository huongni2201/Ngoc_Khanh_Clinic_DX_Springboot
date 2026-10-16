package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record ReactivateServiceCommand(UUID id, long rowVersion) {}
