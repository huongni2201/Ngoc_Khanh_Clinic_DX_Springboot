package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

public record PermissionRecord(UUID id, String code, String name, String description) {}
