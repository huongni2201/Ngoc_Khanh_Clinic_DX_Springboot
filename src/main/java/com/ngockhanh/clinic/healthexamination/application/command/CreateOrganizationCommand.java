package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

@Builder
public record CreateOrganizationCommand(
        String code,
        String name,
        String taxCode,
        String address,
        String contactName,
        String contactPhone,
        String contactJobTitle,
        String note) {
}
