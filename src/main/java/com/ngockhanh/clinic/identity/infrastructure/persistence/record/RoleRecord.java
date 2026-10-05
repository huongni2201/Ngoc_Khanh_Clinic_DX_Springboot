package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

@Builder
public record RoleRecord(UUID id, String code, String name, String description, boolean active) {}
