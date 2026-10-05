package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

@Builder
public record PermissionRecord(UUID id, String code, String name, String description) {}
