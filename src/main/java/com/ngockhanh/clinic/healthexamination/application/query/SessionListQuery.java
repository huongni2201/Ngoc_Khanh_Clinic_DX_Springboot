package com.ngockhanh.clinic.healthexamination.application.query;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Raw input of the session list. {@code null} fields fall back to defaults; the use case validates
 * every value so each supported caller gets the same bounds.
 *
 * @param agreementId only the sessions of that agreement and the shared ones
 * @param from first session date
 * @param to last session date
 */
@Builder
public record SessionListQuery(
    Integer page,
    Integer size,
    String searchKey,
    UUID agreementId,
    LocalDate from,
    LocalDate to,
    String activity,
    String status,
    String sortKey,
    String sortBy) {}
