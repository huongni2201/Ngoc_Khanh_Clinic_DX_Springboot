package com.ngockhanh.clinic.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class BasePaginationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void appliesDefaultsToConstructorAndBuilder() {
        BasePagination constructed = new BasePagination();
        BasePagination built = BasePagination.builder().build();

        assertDefaults(constructed);
        assertDefaults(built);
    }

    @Test
    void rejectsPageSizesAboveTheSharedMaximum() {
        BasePagination pagination = BasePagination.builder().size(101).build();

        assertThat(validator.validate(pagination))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("size"));
    }

    private void assertDefaults(BasePagination pagination) {
        assertThat(pagination.getPage()).isEqualTo(1);
        assertThat(pagination.getSize()).isEqualTo(10);
        assertThat(pagination.getSortKey()).isEqualTo("id");
        assertThat(pagination.getSortBy()).isEqualTo("ASC");
        assertThat(pagination.getSearchKey()).isNull();
    }
}
