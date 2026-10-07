package com.ngockhanh.clinic.healthexamination.application.query;

import lombok.Builder;

/**
 * Validated filters, ordering and window of one examination detail page. Every value already passed
 * the use case's allowlists; {@code null} filters mean "do not filter".
 *
 * @param searchPattern lower-case contains pattern whose LIKE wildcards were escaped, or null
 * @param rosterStatus roster status to keep; the use case defaults it to {@code ACTIVE}
 * @param sortKey allowlisted sort key
 * @param sortBy {@code ASC} or {@code DESC}
 */
@Builder
public record ExaminationDetailCriteria(
    long offset,
    int limit,
    String searchPattern,
    String rosterStatus,
    String attendanceStatus,
    String reconciliationStatus,
    String sortKey,
    String sortBy) {}
