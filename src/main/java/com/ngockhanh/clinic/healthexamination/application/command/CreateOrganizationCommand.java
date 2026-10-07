package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

@Builder
public record CreateOrganizationCommand(
    String name,
    String taxCode,
    String phone,
    String email,
    String address,
    String contactFullName,
    String contactPhone,
    String contactEmail) {}
