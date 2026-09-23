package com.ngockhanh.clinic.shared;

import java.util.UUID;

@FunctionalInterface
public interface IdGenerator {
    UUID next();
}