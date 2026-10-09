package com.ngockhanh.clinic.healthexamination;

import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDetails;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Synthetic batch and organization data for unit tests. */
public final class BatchFixtures {
  public static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 4);
  public static final LocalDate SECOND_DAY = LocalDate.of(2026, 10, 5);
  public static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");

  private BatchFixtures() {}

  public static Organization organization(UUID id) {
    return Organization.create(
        new AggregateId(id),
        "Clinic Partner",
        null,
        "0901",
        "office@example.test",
        "Address",
        "Contact Person",
        "0902",
        "contact@example.test");
  }

  public static Organization inactiveOrganization(UUID id) {
    Organization organization = organization(id);
    organization.deactivate();
    return organization;
  }

  /** A configuration for the given catalog services, each negotiated at 100. */
  public static BatchConfiguration configuration(UUID... services) {
    List<BatchConfiguration.ServicePrice> prices = new ArrayList<>();
    for (UUID service : services)
      prices.add(
          BatchConfiguration.ServicePrice.builder()
              .serviceId(service)
              .negotiatedPrice(new BigDecimal("100"))
              .build());
    return BatchConfiguration.builder()
        .batchName("Campaign")
        .examinationDates(List.of(FIRST_DAY))
        .examinationSiteType("CLINIC")
        .examinationSiteName("Clinic")
        .examinationSiteAddress("Address")
        .services(prices)
        .build();
  }

  /**
   * A stored draft batch with two days ({@link #FIRST_DAY}, {@link #SECOND_DAY}) and one service
   * per given catalog service: reference price 200, negotiated price 100, display order 1..n,
   * active, row version 0.
   */
  public static HealthExaminationBatch draftBatch(
      UUID organizationId, UUID batchId, long rowVersion, UUID... services) {
    return batch(organizationId, batchId, BatchStatus.DRAFT, rowVersion, null, services);
  }

  public static HealthExaminationBatch batch(
      UUID organizationId,
      UUID batchId,
      BatchStatus status,
      long rowVersion,
      Instant deletedAt,
      UUID... services) {
    var batch = new AggregateId(batchId);
    List<HealthExaminationBatchService> stored = new ArrayList<>();
    int order = 1;
    for (UUID service : services)
      stored.add(
          new HealthExaminationBatchService(
              new AggregateId(UUID.randomUUID()),
              new AggregateId(service),
              batch,
              Money.vnd("200"),
              Money.vnd("100"),
              order++,
              true,
              0));
    return HealthExaminationBatch.restoreConfiguration(
        batch,
        new AggregateId(organizationId),
        "B1",
        "Campaign",
        new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", "Address"),
        List.of(
            new HealthExaminationBatchDay(UUID.randomUUID(), FIRST_DAY),
            new HealthExaminationBatchDay(UUID.randomUUID(), SECOND_DAY)),
        stored,
        status,
        rowVersion,
        deletedAt);
  }

  public static BatchDetails details(HealthExaminationBatch batch) {
    return new BatchDetails(batch, UUID.randomUUID(), CREATED_AT, CREATED_AT);
  }
}
