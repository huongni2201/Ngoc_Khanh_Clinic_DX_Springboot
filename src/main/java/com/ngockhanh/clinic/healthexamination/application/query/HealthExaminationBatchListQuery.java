package com.ngockhanh.clinic.healthexamination.application.query;

public record HealthExaminationBatchListQuery(
    Integer page, Integer size, String searchKey, String sortKey, String sortBy) {}
