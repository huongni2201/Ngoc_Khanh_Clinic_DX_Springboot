package com.ngockhanh.clinic.healthexamination.application.query;

import lombok.Builder;

/**
 * Raw input of the Participant list of a session. {@code null} fields fall back to defaults.
 *
 * @param status only assignments in that status
 */
@Builder
public record AssignmentListQuery(
    Integer page, Integer size, String searchKey, String status, String sortKey, String sortBy) {}
