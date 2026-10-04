package com.ngockhanh.clinic.integration.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.integration.infrastructure.persistence.record.ImportJobRecord;
import com.ngockhanh.clinic.integration.infrastructure.persistence.record.ImportRowRecord;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImportMapper {
  boolean hasBatchDayReferences(@Param("batchId") UUID batchId, @Param("dayIds") List<UUID> dayIds);

  ImportJobRecord find(
      @Param("id") UUID id, @Param("batchId") UUID batchId, @Param("forUpdate") boolean forUpdate);

  List<ImportRowRecord> rows(UUID jobId);

  int insertJob(ImportJobRecord job);

  int updateJob(@Param("job") ImportJobRecord job, @Param("expectedVersion") long expectedVersion);

  int saveRows(@Param("rows") List<ImportRowRecord> rows);

  int updateRows(@Param("rows") List<ImportRowRecord> rows);
}
