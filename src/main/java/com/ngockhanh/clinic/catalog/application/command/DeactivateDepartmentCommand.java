package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record DeactivateDepartmentCommand(UUID id, long rowVersion) {}
