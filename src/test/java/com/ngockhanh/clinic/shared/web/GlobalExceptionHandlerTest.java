package com.ngockhanh.clinic.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.healthcheck.domain.exception.BatchConfigurationLocked;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsKnownFailuresAndHidesUnexpectedDetails() {
        assertThat(handler.businessRule(new BatchConfigurationLocked()).getStatusCode().value()).isEqualTo(409);
        assertThat(handler.invalidInput(new IllegalArgumentException("internal validation detail")).getBody().message()).isEqualTo("Invalid request");
        assertThat(handler.duplicate(new org.springframework.dao.DuplicateKeyException("unique index")).getStatusCode().value()).isEqualTo(409);
        assertThat(handler.notFound(new ResourceNotFoundException("patient")).getStatusCode().value()).isEqualTo(404);
        assertThat(handler.concurrency(new ConcurrentUpdateException()).getBody().code()).isEqualTo("CONCURRENCY_CONFLICT");
        ApiError unexpected = handler.unexpected(new RuntimeException("secret database detail")).getBody();
        assertThat(unexpected.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(unexpected.message()).doesNotContain("secret");
    }
}
