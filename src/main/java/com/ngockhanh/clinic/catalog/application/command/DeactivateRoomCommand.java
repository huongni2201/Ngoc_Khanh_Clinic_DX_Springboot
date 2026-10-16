package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record DeactivateRoomCommand(UUID id, long rowVersion) {}
