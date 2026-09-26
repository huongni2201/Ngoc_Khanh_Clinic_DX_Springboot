package com.ngockhanh.clinic.shared.id;

import java.util.UUID;

@FunctionalInterface
public interface IdGenerator {
    UUID next();
}