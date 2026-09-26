package com.ngockhanh.clinic.healthcheck.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record OrganizationId(UUID value) {
    public OrganizationId {
        Objects.requireNonNull(value, "Organization ID must not be null");
    }
}
