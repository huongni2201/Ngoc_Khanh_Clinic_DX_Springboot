package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

public record CreateRoomCommand(String code, String name, UUID departmentId, String roomType) {}
