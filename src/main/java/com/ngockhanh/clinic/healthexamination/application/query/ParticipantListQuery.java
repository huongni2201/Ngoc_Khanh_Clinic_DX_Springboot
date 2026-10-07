package com.ngockhanh.clinic.healthexamination.application.query;

import java.util.UUID;
import lombok.Builder;

/**
 * Raw input of the Participant list. {@code null} fields fall back to defaults; the use case
 * validates every value so each supported caller gets the same bounds.
 */
@Builder
public record ParticipantListQuery(
    Integer page,
    Integer size,
    String searchKey,
    String identificationNumber,
    UUID batchDayId,
    String rosterStatus,
    String attendanceStatus,
    String reconciliationStatus,
    String sortKey,
    String sortBy) {}
