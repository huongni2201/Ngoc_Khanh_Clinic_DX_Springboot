package com.ngockhanh.clinic.healthexamination.domain;

import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
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
    var late = new HealthExaminationBatchDay(UUID.randomUUID(), LocalDate.of(2026, 10, 8));
    var early = new HealthExaminationBatchDay(UUID.randomUUID(), LocalDate.of(2026, 10, 4));
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
    assertThatThrownBy(
            () ->
                HealthExaminationBatch.createDraft(
                    id,
                    org,
                    "B",
                    "Batch",
                    site,
                    List.of(
                        early,
                        new HealthExaminationBatchDay(UUID.randomUUID(), early.examinationDate())),
                    List.of(service)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                HealthExaminationBatch.createDraft(
                    id, org, "B", "Batch", site, List.of(early), List.of()))
        .isInstanceOf(RuntimeException.class);
    var b =
        HealthExaminationBatch.createDraft(
            id, org, "B", "Batch", site, List.of(late, early), List.of(service));
    assertThat(b.startDate()).isEqualTo(LocalDate.of(2026, 10, 4));
    assertThat(b.endDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    assertThat(b.days()).containsExactly(early, late);
  }

  @Test
  void batchTransitionsThroughTheFourSupportedStates() {
    var id = id();
    var service =
        new HealthExaminationBatchService(
            id(), id(), id, Money.vnd("200"), Money.vnd("100"), 1, true, 0);
    var day = new HealthExaminationBatchDay(UUID.randomUUID(), LocalDate.of(2026, 10, 4));
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
  }

  @Test
  void batchDayRequiresIdAndDate() {
    assertThatThrownBy(() -> new HealthExaminationBatchDay(null, LocalDate.of(2026, 10, 4)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new HealthExaminationBatchDay(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
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

  private HealthExaminationBatch draft() {
    var id = id();
    var service =
        new HealthExaminationBatchService(
            id(), id(), id, Money.vnd("200"), Money.vnd("100"), 1, true, 0);
    var day = new HealthExaminationBatchDay(UUID.randomUUID(), LocalDate.of(2026, 10, 4));
    return HealthExaminationBatch.createDraft(
        id, id(), "B", "Batch", site, List.of(day), List.of(service));
  }

  @Test
  void updateDraftReplacesTheWholeConfigurationAndRederivesTheDateRange() {
    var b = draft();
    var batchId = b.id();
    var newSite = new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", "Other");
    var later = new HealthExaminationBatchDay(UUID.randomUUID(), LocalDate.of(2026, 11, 2));
    var earlier = new HealthExaminationBatchDay(UUID.randomUUID(), LocalDate.of(2026, 11, 1));
    var service =
        new HealthExaminationBatchService(
            id(), id(), batchId, Money.vnd("300"), Money.vnd("250"), 1, true, 0);

    b.updateDraft("B2", "Renamed", newSite, List.of(later, earlier), List.of(service));

    assertThat(b.code()).isEqualTo("B2");
    assertThat(b.name()).isEqualTo("Renamed");
    assertThat(b.site()).isEqualTo(newSite);
    assertThat(b.startDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(b.endDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(b.services()).containsExactly(service);
    assertThat(b.id()).isEqualTo(batchId);
  }

  @Test
  void updateDraftKeepsTheOldConfigurationWhenTheNewOneIsInvalid() {
    var b = draft();
    var before = b.days();

    assertThatThrownBy(() -> b.updateDraft("B2", "Renamed", site, List.of(), b.services()))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(b.code()).isEqualTo("B");
    assertThat(b.days()).isEqualTo(before);
  }

  @Test
  void onlyAnUndeletedDraftCanBeEditedOrDeleted() {
    var b = draft();
    b.requireDraft();
    b.markReady();
    assertThatThrownBy(b::requireDraft)
        .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation.class);
    assertThatThrownBy(() -> b.updateDraft("B2", "N", site, b.days(), b.services()))
        .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation.class);
    assertThatThrownBy(() -> b.softDelete(java.time.Instant.now()))
        .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation.class);
  }

  @Test
  void softDeleteMarksTheDraftDeletedAndCannotBeRepeatedOrFollowedByAnEdit() {
    var b = draft();
    var at = java.time.Instant.parse("2026-10-06T03:00:00Z");
    assertThat(b.isDeleted()).isFalse();
    assertThat(b.deletedAt()).isNull();

    b.softDelete(at);

    assertThat(b.isDeleted()).isTrue();
    assertThat(b.deletedAt()).isEqualTo(at);
    assertThat(b.status()).isEqualTo(BatchStatus.DRAFT);
    assertThatThrownBy(() -> b.softDelete(at))
        .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation.class);
    assertThatThrownBy(() -> b.updateDraft("B2", "N", site, b.days(), b.services()))
        .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation.class);
    assertThatThrownBy(() -> draft().softDelete(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
