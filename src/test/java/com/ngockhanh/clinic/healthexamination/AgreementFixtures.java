package com.ngockhanh.clinic.healthexamination;

import com.ngockhanh.clinic.healthexamination.application.command.AgreementConfiguration;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository.AgreementDetails;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Synthetic agreement and organization data for unit tests. */
public final class AgreementFixtures {
  public static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 4);
  public static final LocalDate SECOND_DAY = LocalDate.of(2026, 10, 5);
  public static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");

  private AgreementFixtures() {}

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
  public static AgreementConfiguration configuration(UUID... services) {
    List<AgreementConfiguration.ServicePrice> prices = new ArrayList<>();
    for (UUID service : services)
      prices.add(
          AgreementConfiguration.ServicePrice.builder()
              .serviceId(service)
              .negotiatedPrice(new BigDecimal("100"))
              .build());
    return AgreementConfiguration.builder()
        .agreementName("Campaign")
        .validFrom(FIRST_DAY)
        .validTo(SECOND_DAY)
        .services(prices)
        .build();
  }

  /**
   * A stored draft agreement valid from {@link #FIRST_DAY} to {@link #SECOND_DAY} with one service
   * per given catalog service: reference price 200, negotiated price 100, display order 1..n,
   * active, row version 0.
   */
  public static HealthExaminationAgreement draftAgreement(
      UUID organizationId, UUID agreementId, long rowVersion, UUID... services) {
    return agreement(
        organizationId, agreementId, AgreementStatus.DRAFT, rowVersion, null, services);
  }

  public static HealthExaminationAgreement agreement(
      UUID organizationId,
      UUID agreementId,
      AgreementStatus status,
      long rowVersion,
      Instant deletedAt,
      UUID... services) {
    var agreement = new AggregateId(agreementId);
    List<HealthExaminationAgreementService> stored = new ArrayList<>();
    int order = 1;
    for (UUID service : services)
      stored.add(
          new HealthExaminationAgreementService(
              new AggregateId(UUID.randomUUID()),
              new AggregateId(service),
              agreement,
              Money.vnd("200"),
              Money.vnd("100"),
              order++,
              true,
              0));
    return HealthExaminationAgreement.restoreConfiguration(
        agreement,
        new AggregateId(organizationId),
        "B1",
        "Campaign",
        FIRST_DAY,
        SECOND_DAY,
        stored,
        status,
        rowVersion,
        deletedAt);
  }

  public static AgreementDetails details(HealthExaminationAgreement agreement) {
    return new AgreementDetails(agreement, UUID.randomUUID(), CREATED_AT, CREATED_AT);
  }
}
