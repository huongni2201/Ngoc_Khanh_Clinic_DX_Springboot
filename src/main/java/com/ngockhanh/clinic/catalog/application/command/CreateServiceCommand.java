package com.ngockhanh.clinic.catalog.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateServiceCommand(
    String code,
    String name,
    String serviceType,
    UUID performingDepartmentId,
    UUID specialtyId,
    BigDecimal unitPrice,
    String preparationInstruction) {}
