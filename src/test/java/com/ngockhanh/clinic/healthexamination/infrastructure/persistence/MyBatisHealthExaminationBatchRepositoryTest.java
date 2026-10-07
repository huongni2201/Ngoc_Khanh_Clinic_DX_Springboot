package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchSummary;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisHealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.HealthExaminationBatchSummaryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyBatisHealthExaminationBatchRepositoryTest {
  @Test
  void mapsTheSqlSummaryProjectionToPageSummary() {
    var mapper = mock(HealthExaminationBatchMyBatisMapper.class);
    var repository = new MyBatisHealthExaminationBatchRepository(mapper);
    var organizationId = UUID.randomUUID();
    var batchId = UUID.randomUUID();
    var createdAt = Instant.parse("2026-01-01T10:00:00Z");
    var updatedAt = Instant.parse("2026-01-02T10:00:00Z");
    var firstDate = LocalDate.of(2026, 1, 10);
    var lastDate = LocalDate.of(2026, 1, 12);
    when(mapper.findPage(organizationId, 0, 10, null, "batchCode", "ASC"))
        .thenReturn(
            List.of(
                new HealthExaminationBatchSummaryView(
                    batchId,
                    "BATCH-001",
                    "Annual examination",
                    firstDate,
                    lastDate,
                    "DRAFT",
                    createdAt,
                    updatedAt,
                    3)));

    var result = repository.findPage(organizationId, 0, 10, null, "batchCode", "ASC");

    assertThat(result)
        .containsExactly(
            new BatchSummary(
                batchId,
                "BATCH-001",
                "Annual examination",
                firstDate,
                lastDate,
                BatchStatus.DRAFT,
                createdAt,
                updatedAt,
                3));
  }
}
