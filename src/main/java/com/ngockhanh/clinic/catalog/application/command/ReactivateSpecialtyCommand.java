package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record ReactivateSpecialtyCommand(UUID id, long rowVersion) {}
