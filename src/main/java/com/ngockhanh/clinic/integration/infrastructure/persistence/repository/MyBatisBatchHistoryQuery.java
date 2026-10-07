package com.ngockhanh.clinic.integration.infrastructure.persistence.repository;

import com.ngockhanh.clinic.integration.application.query.BatchHistoryQuery;
import com.ngockhanh.clinic.integration.infrastructure.persistence.mapper.BatchHistoryMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisBatchHistoryQuery implements BatchHistoryQuery {
  private final BatchHistoryMapper mapper;

  @Override
  public boolean hasBatchReferences(UUID batchId) {
    if (batchId == null) throw new IllegalArgumentException("Batch ID is required");
    return mapper.existsImportJobForBatch(batchId);
  }
}
