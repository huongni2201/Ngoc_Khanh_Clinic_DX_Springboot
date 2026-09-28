package com.ngockhanh.clinic.healthcheck.application.port;

import java.util.List;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.api.response.EmployeeListResponse;

public interface BatchEmployeeSummaryQuery {
  List<EmployeeListResponse> findByBatch(
      UUID batchId,
      int offset,
      int limit,
      String searchPattern,
      String sortType,
      String sortBy
  );

  long countByBatch(UUID batchId, String searchPattern);
}
