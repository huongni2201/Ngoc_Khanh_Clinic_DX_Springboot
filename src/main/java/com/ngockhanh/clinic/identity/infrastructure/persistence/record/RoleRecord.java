package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

public record RoleRecord(UUID id, String code, String name, String description, boolean active) {}
