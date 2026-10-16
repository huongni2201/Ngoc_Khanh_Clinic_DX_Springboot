package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record UpdateSpecialtyCommand(UUID id, String name, long rowVersion) {}
