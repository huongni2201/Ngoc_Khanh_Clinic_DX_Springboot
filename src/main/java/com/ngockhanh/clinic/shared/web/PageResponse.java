package com.ngockhanh.clinic.shared.web;

import java.util.List;
import lombok.Builder;

@Builder
public record PageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {}
