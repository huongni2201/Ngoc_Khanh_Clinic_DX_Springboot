package com.ngockhanh.clinic.catalog.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateServiceCommand(
    UUID id,
    String name,
    String serviceType,
    UUID specialtyId,
    BigDecimal unitPrice,
    String preparationInstruction,
    long rowVersion) {}
