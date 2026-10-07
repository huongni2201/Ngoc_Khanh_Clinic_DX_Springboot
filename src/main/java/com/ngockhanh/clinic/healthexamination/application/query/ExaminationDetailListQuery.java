package com.ngockhanh.clinic.healthexamination.application.query;

import lombok.Builder;

/**
 * Raw input of the examination detail list. {@code null} fields fall back to defaults; the use case
 * validates every value so each supported caller gets the same bounds.
 */
@Builder
public record ExaminationDetailListQuery(
    Integer page,
    Integer size,
    String searchKey,
    String rosterStatus,
    String attendanceStatus,
    String reconciliationStatus,
    String sortKey,
    String sortBy) {}
