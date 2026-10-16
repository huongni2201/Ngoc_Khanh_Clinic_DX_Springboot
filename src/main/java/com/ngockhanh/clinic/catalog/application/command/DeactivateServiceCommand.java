package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record DeactivateServiceCommand(UUID id, long rowVersion) {}
