package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record UpdateRoomCommand(UUID id, String name, String roomType, long rowVersion) {}
