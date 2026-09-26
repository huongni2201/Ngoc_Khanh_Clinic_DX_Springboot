package com.ngockhanh.clinic.shared.infrastructure.id;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.github.f4b6a3.uuid.UuidCreator;
import com.ngockhanh.clinic.shared.id.IdGenerator;

@Component
public final class UuidV7Generator implements IdGenerator {
    @Override
    public UUID next() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
