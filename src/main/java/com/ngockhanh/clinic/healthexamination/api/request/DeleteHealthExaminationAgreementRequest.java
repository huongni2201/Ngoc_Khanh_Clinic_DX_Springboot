package com.ngockhanh.clinic.healthexamination.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

/**
 * Query parameters for soft-deleting a draft health examination agreement.
 *
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record DeleteHealthExaminationAgreementRequest(@NotNull @PositiveOrZero Long rowVersion) {}
