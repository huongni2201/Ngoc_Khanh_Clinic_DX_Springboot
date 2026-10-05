package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisHealthExaminationImportJobRepository;
import com.ngockhanh.clinic.integration.application.imports.ImportStore;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MyBatisHealthExaminationImportJobRepositoryTest {
  @Test
  void storesOnlyGenericPayloadAndPreviewAndRestoresApprovedAssignment() {
    var store = mock(ImportStore.class);
    var json = JsonMapper.builder().findAndAddModules().build();
    var repository = new MyBatisHealthExaminationImportJobRepository(store, json);
    var job = job(4);
    repository.save(job);
    var jobCaptor = org.mockito.ArgumentCaptor.forClass(ImportStore.Job.class);
    var rowsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(store).update(jobCaptor.capture(), rowsCaptor.capture(), eq(4L));
    assertThat(jobCaptor.getValue().configuration()).contains("selectedBatchDayIds");
    assertThat(job.rowVersion()).isEqualTo(5);
    when(store.find(id(5).value(), id(1).value(), true))
        .thenReturn(Optional.of(jobCaptor.getValue()));
    when(store.rows(id(5).value())).thenReturn(rowsCaptor.getValue());
    var loaded = repository.findByIdAndBatchIdForUpdate(id(5), id(1)).orElseThrow();
    assertThat(loaded.rows().getFirst().getBatchDayId()).isEqualTo(id(3));
    assertThat(loaded.rows().getFirst().getIdentificationNumber().value())
        .isEqualTo("000000000001");
  }

  @Test
  void readsSummaryAndRowPageWithoutLoadingAllStagedRows() {
    var store = mock(ImportStore.class);
    var json = JsonMapper.builder().findAndAddModules().build();
    var repository = new MyBatisHealthExaminationImportJobRepository(store, json);
    UUID jobId = id(5).value();
    UUID batchId = id(1).value();
    UUID dayId = id(3).value();
    var now = java.time.Instant.parse("2026-10-04T00:00:00Z");
    var job =
        new ImportStore.Job(
            jobId,
            "ORGANIZATION_PARTICIPANT",
            batchId,
            json.writeValueAsString(
                new MyBatisHealthExaminationImportJobRepository.Configuration(List.of(dayId))),
            null,
            "VALIDATED",
            id(6).value(),
            null,
            now,
            null,
            null,
            null,
            null,
            4);
    var row =
        new ImportStore.Row(
            id(100).value(),
            jobId,
            11,
            json.writeValueAsString(
                new MyBatisHealthExaminationImportJobRepository.Payload(
                    null,
                    "Synthetic Person",
                    java.time.LocalDate.of(1990, 1, 1),
                    "MALE",
                    "000000000011",
                    null,
                    null,
                    "Department",
                    "Position")),
            json.writeValueAsString(new MyBatisHealthExaminationImportJobRepository.Preview(dayId)),
            null,
            null,
            now);
    when(store.find(jobId, batchId, false)).thenReturn(Optional.of(job));
    when(store.countRows(jobId)).thenReturn(2500L);
    when(store.pageRows(jobId, 10, 20)).thenReturn(List.of(row));

    var summary = repository.findSummaryByIdAndBatchId(id(5), id(1)).orElseThrow();
    var rows = repository.findRowsByJobId(id(5), 10, 20);

    assertThat(summary.status()).isEqualTo(ImportStatus.VALIDATED);
    assertThat(summary.selectedBatchDayIds()).containsExactly(id(3));
    assertThat(repository.countRowsByJobId(id(5))).isEqualTo(2500);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getRowNumber()).isEqualTo(11);
    assertThat(rows.getFirst().getIdentificationNumber().value()).isEqualTo("000000000011");
    verify(store, never()).rows(jobId);
    verify(store).countRows(jobId);
    verify(store).pageRows(jobId, 10, 20);
  }
}
