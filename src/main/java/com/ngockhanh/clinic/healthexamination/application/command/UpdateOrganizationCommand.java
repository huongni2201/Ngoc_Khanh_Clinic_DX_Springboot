package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

@Builder
public record UpdateOrganizationCommand(
    String code,
    String name,
    String organizationType,
    String taxCode,
    String phone,
    String email,
    String address,
    String contactFullName,
    String contactPosition,
    String contactPhone,
    String contactEmail,
    Long rowVersion) {}
