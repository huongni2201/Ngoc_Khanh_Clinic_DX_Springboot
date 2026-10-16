package com.ngockhanh.clinic.catalog.application.command;

import java.util.UUID;

/** Command to update an existing department (code is immutable, excluded). */
public record UpdateDepartmentCommand(
    UUID id, String name, String departmentType, long rowVersion) {}
