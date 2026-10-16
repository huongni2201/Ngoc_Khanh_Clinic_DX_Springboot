package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record ReactivateRoomCommand(UUID id, long rowVersion) {}
