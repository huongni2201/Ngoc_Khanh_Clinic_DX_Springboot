package com.ngockhanh.clinic.healthexamination.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record AggregateId(UUID value) {
    public AggregateId {
        Objects.requireNonNull(value, "ID must not be null");
    }

    public static AggregateId of(UUID value) {
        return new AggregateId(value);
    }
}
