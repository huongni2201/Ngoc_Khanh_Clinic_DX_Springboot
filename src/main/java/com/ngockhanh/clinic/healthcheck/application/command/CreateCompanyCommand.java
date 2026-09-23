package com.ngockhanh.clinic.healthcheck.application.command;

public record CreateCompanyCommand(
        String code,
        String name,
        String taxCode,
        String address,
        String contactName,
        String contactPhone,
        String contactJobTitle,
        String note) {
}