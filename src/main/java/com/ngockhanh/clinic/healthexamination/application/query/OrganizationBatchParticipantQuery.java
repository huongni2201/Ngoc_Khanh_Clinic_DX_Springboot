package com.ngockhanh.clinic.healthexamination.application.query;

import lombok.Builder;

@Builder
public record OrganizationBatchParticipantQuery(
    Integer page,
    Integer size,
    String searchKey,
    String sortBy,
    String sortKey
) {}
