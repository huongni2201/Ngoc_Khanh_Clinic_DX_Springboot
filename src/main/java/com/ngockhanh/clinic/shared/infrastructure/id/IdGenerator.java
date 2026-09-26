package com.ngockhanh.clinic.shared.infrastructure.id;

import java.util.UUID;

@FunctionalInterface
public interface IdGenerator {
    UUID next();
}
