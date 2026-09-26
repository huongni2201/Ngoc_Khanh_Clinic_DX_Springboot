package com.ngockhanh.clinic.shared.infrastructure.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {
    private final UuidV7Generator generator = new UuidV7Generator();

    @Test
    void generatesRfcVariantUuidVersion7() {
        UUID id = generator.next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void generatesUniqueIds() {
        Set<UUID> ids = IntStream.range(0, 100_000)
                .mapToObj(ignored -> generator.next())
                .collect(HashSet::new, Set::add, Set::addAll);

        assertThat(ids).hasSize(100_000);
    }
}
