package com.ngockhanh.clinic.healthexamination.application.query;

import lombok.Builder;

@Builder
public record HealthExaminationAgreementListQuery(
    Integer page, Integer size, String searchKey, String sortKey, String sortBy) {}
