package com.ngockhanh.clinic.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {
    @Test
    void generatesTimeOrderedUuidV7Values() {
        UuidV7Generator generator = new UuidV7Generator();

        UUID first = generator.next();
        UUID second = generator.next();

        assertThat(first.version()).isEqualTo(7);
        assertThat(first.variant()).isEqualTo(2);
        assertThat(second.version()).isEqualTo(7);
        assertThat(second.variant()).isEqualTo(2);
        assertThat(second.compareTo(first)).isPositive();
    }
}