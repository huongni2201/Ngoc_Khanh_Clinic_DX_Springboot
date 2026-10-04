package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    when(store.find(id(5).value(), id(1).value(), false))
        .thenReturn(Optional.of(jobCaptor.getValue()));
    when(store.rows(id(5).value())).thenReturn(rowsCaptor.getValue());
    var loaded = repository.findByIdAndBatchId(id(5), id(1)).orElseThrow();
    assertThat(loaded.rows().getFirst().getBatchDayId()).isEqualTo(id(3));
    assertThat(loaded.rows().getFirst().getIdentificationNumber().value())
        .isEqualTo("000000000001");
  }
}
