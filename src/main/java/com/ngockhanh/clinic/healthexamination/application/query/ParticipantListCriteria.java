package com.ngockhanh.clinic.healthexamination.application.query;

import java.util.UUID;
import lombok.Builder;

/**
 * Validated filters, ordering and window of one Participant page. Every value already passed the
 * use case's allowlists; {@code null} filters mean "do not filter".
 *
 * @param searchPattern lower-case contains pattern whose LIKE wildcards were escaped, or null
 * @param identificationNumber exact identification number to match, or null
 * @param sortKey allowlisted sort key
 * @param sortBy {@code ASC} or {@code DESC}
 */
@Builder
public record ParticipantListCriteria(
    long offset,
    int limit,
    String searchPattern,
    String identificationNumber,
    UUID batchDayId,
    String rosterStatus,
    String attendanceStatus,
    String reconciliationStatus,
    String sortKey,
    String sortBy) {}
