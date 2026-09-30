package com.ngockhanh.clinic.healthexamination.domain;

import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HealthExaminationBatchCrudTest {
  private AggregateId id() {
    return new AggregateId(UUID.randomUUID());
  }

  @Test
  void editsDraftAtomicallyAndSoftDeleteLocksIt() {
    var batch =
        HealthExaminationBatch.create(
            id(), id(), "B1", new ExaminationSite(ExaminationSiteType.COMPANY, "Site", null), id());
    var service =
        HealthExaminationBatchService.create(
            id(), id(), batch.id(), "S1", "Exam", Money.vnd("100"), null, 1, "ACTIVE");
    batch.updateDraft("B2", "Campaign", null, null, null, null, batch.site(), List.of(service));
    assertThat(batch.name()).isEqualTo("Campaign");
    assertThatThrownBy(batch::markReady).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                batch.updateDraft(
                    "BAD", "Bad", null, null, null, null, batch.site(), List.of(service, service)))
        .isInstanceOf(RuntimeException.class);
    assertThat(batch.code()).isEqualTo("B2");
    batch.deleteDraft();
    assertThat(batch.status()).isEqualTo(BatchStatus.DELETED);
    assertThat(batch.services()).containsExactly(service);
    assertThatThrownBy(
            () ->
                batch.updateDraft(
                    "B3", "Other", null, null, null, null, batch.site(), List.of(service)))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> batch.repriceService(service.id(), Money.vnd("200"), "change"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void rejectsEmptyScopeAndInvalidDates() {
    assertThatThrownBy(() -> Money.vnd("10000000000000000"))
        .isInstanceOf(IllegalArgumentException.class);
    var batch =
        HealthExaminationBatch.create(
            id(),
            id(),
            "B1",
            new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null),
            id());
    var service =
        HealthExaminationBatchService.create(
            id(), id(), batch.id(), "S1", "Exam", Money.vnd("100"), null, 1, "ACTIVE");
    assertThatThrownBy(
            () ->
                batch.updateDraft(
                    "B1",
                    "Name",
                    java.time.LocalDate.of(2026, 9, 30),
                    java.time.LocalDate.of(2026, 9, 29),
                    null,
                    null,
                    batch.site(),
                    List.of(service)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> batch.updateDraft("B1", "Name", null, null, null, null, batch.site(), List.of()))
        .isInstanceOf(RuntimeException.class);
  }
}
