package com.ngockhanh.clinic.healthexamination.domain;

import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDay;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class HealthExaminationBatchCrudTest {
  private AggregateId id() {
    return new AggregateId(UUID.randomUUID());
  }

  private final ExaminationSite site =
      new ExaminationSite(ExaminationSiteType.ORGANIZATION_SITE, "Site", "Address");

  @Test
  void requiresAtLeastOneDistinctDayAndDerivesDateRange() {
    var id = id();
    var org = id();
    var service =
        new HealthExaminationBatchService(
            id(), id(), id, Money.vnd("200"), Money.vnd("100"), 1, true, 0);
    var late = new BatchDay(UUID.randomUUID(), LocalDate.of(2026, 10, 8));
    var early = new BatchDay(UUID.randomUUID(), LocalDate.of(2026, 10, 4));
    assertThatThrownBy(
            () ->
                HealthExaminationBatch.createDraft(
                    id, org, "B", "Batch", site, List.of(), List.of(service)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                HealthExaminationBatch.createDraft(
                    id, org, "B", "Batch", site, List.of(early, early), List.of(service)))
        .isInstanceOf(IllegalArgumentException.class);
    var b =
        HealthExaminationBatch.createDraft(
            id, org, "B", "Batch", site, List.of(late, early), List.of(service));
    assertThat(b.startDate()).isEqualTo(LocalDate.of(2026, 10, 4));
    assertThat(b.endDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    assertThat(b.days()).containsExactly(early, late);
  }

  @Test
  void onlyFourStatesRemainAndFinalizationLocksConfiguration() {
    var id = id();
    var service =
        new HealthExaminationBatchService(
            id(), id(), id, Money.vnd("200"), Money.vnd("100"), 1, true, 0);
    var day = new BatchDay(UUID.randomUUID(), LocalDate.of(2026, 10, 4));
    var b =
        HealthExaminationBatch.createDraft(
            id, id(), "B", "Batch", site, List.of(day), List.of(service));
    b.markReady();
    b.finalizeBatch();
    b.close();
    assertThat(b.status()).isEqualTo(BatchStatus.CLOSED);
    assertThat(BatchStatus.values())
        .containsExactly(
            BatchStatus.DRAFT, BatchStatus.READY, BatchStatus.FINALIZED, BatchStatus.CLOSED);
    assertThatThrownBy(() -> b.updateDraft("B2", "Changed", site, List.of(day), List.of(service)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void batchPricesUseNumericFourteenTwo() {
    var id = id();
    assertThatThrownBy(
            () ->
                new HealthExaminationBatchService(
                    id(), id(), id, Money.vnd("1000000000000"), Money.vnd("1"), 1, true, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
