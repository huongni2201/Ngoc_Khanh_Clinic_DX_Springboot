package com.ngockhanh.clinic.catalog.application.command;

/** Command to create a new department. */
public record CreateDepartmentCommand(String code, String name, String departmentType) {}
