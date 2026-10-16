package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.AgreementFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.AgreementConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.service.AgreementCodeGenerator;
import com.ngockhanh.clinic.healthexamination.application.service.AgreementSupport;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgreementConfigurationUseCaseTest {
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final AgreementSupport support = new AgreementSupport(catalog);
  private final CreateHealthExaminationAgreementUseCase create =
      new CreateHealthExaminationAgreementUseCase(
          organizations,
          agreements,
          support,
          new AgreementCodeGenerator(
              agreements, Clock.fixed(Instant.parse("2026-10-08T03:00:00Z"), ZoneOffset.UTC)),
          audit);
  private final UpdateHealthExaminationAgreementUseCase update =
      new UpdateHealthExaminationAgreementUseCase(organizations, agreements, support, audit);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();

  private HealthExaminationAgreement create(AgreementConfiguration configuration) {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    var inserted = ArgumentCaptor.forClass(HealthExaminationAgreement.class);
    when(agreements.findDetails(eq(organizationId), any(), eq(false)))
        .thenAnswer(
            call -> {
              verify(agreements).insert(inserted.capture(), eq(actor));
              return Optional.of(details(inserted.getValue()));
            });
    create.execute(
        organizationId,
        CreateHealthExaminationAgreementCommand.builder().configuration(configuration).build(),
        actor);
    return inserted.getValue();
  }

  private HealthExaminationAgreement update(
      HealthExaminationAgreement current, AgreementConfiguration configuration) {
    var owner = current.organizationId().value();
    var id = current.id().value();
    when(organizations.findById(current.organizationId()))
        .thenReturn(Optional.of(organization(owner)));
    when(agreements.findDetails(owner, id, true)).thenReturn(Optional.of(details(current)));
    when(agreements.findDetails(owner, id, false)).thenReturn(Optional.of(details(current)));
    update.execute(
        owner,
        id,
        UpdateHealthExaminationAgreementCommand.builder()
            .configuration(configuration)
            .rowVersion(current.rowVersion())
            .build(),
        actor);
    verify(agreements).update(current, current.rowVersion());
    return current;
  }

  private final AggregateId agreementId = new AggregateId(UUID.randomUUID());

  private static ServiceCatalogQuery.Service catalogService(UUID id, boolean active, String price) {
    return new ServiceCatalogQuery.Service(id, "S", "Exam", active, new BigDecimal(price));
  }

  private static AgreementConfiguration with(
      AgreementConfiguration base,
      java.util.function.UnaryOperator<AgreementConfiguration.AgreementConfigurationBuilder>
          change) {
    return change.apply(base.toBuilder()).build();
  }

  private static AgreementConfiguration.ServicePrice price(UUID service, String price) {
    return AgreementConfiguration.ServicePrice.builder()
        .serviceId(service)
        .negotiatedPrice(new BigDecimal(price))
        .build();
  }

  @Test
  void createsServicesWithNewIdsAndCatalogPriceSnapshots() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    when(catalog.findByIds(Set.of(first, second)))
        .thenReturn(
            List.of(catalogService(first, true, "200"), catalogService(second, true, "300")));

    var assembled = create(configuration(first, second));

    assertThat(assembled.code()).isEqualTo("KSK-2026-001");
    assertThat(assembled.validFrom()).isEqualTo(FIRST_DAY);
    assertThat(assembled.validTo()).isEqualTo(SECOND_DAY);
    assertThat(assembled.services())
        .extracting(s -> s.serviceId().value())
        .containsExactly(first, second);
    assertThat(assembled.services())
        .extracting(s -> s.referencePriceSnapshot().amount())
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("200"), new BigDecimal("300"));
    assertThat(assembled.services())
        .extracting(s -> s.negotiatedPrice().amount())
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("100"), new BigDecimal("100"));
    assertThat(assembled.services())
        .extracting(HealthExaminationAgreementService::displayOrder)
        .containsExactly(1, 2);
    assertThat(assembled.services())
        .allSatisfy(
            s -> {
              assertThat(s.agreementId()).isEqualTo(assembled.id());
              assertThat(s.rowVersion()).isZero();
              assertThat(s.active()).isTrue();
            });
  }

  @Test
  void aValidityPeriodIsOptional() {
    UUID service = UUID.randomUUID();
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(List.of(catalogService(service, true, "200")));

    var assembled = create(with(configuration(service), b -> b.validFrom(null).validTo(null)));

    assertThat(assembled.validFrom()).isNull();
    assertThat(assembled.validTo()).isNull();
  }

  @Test
  void rejectsInvalidPeriodAndPrices() {
    UUID service = UUID.randomUUID();
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(List.of(catalogService(service, true, "200")));
    var valid = configuration(service);

    assertThatThrownBy(() -> create(with(valid, b -> b.validFrom(SECOND_DAY).validTo(FIRST_DAY))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(with(valid, b -> b.services(List.of()))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(with(valid, b -> b.services(null))))
        .isInstanceOf(IllegalArgumentException.class);
    for (String invalidPrice : List.of("-1.00", "1.234", "1000000000000")) {
      assertThatThrownBy(
              () -> create(with(valid, b -> b.services(List.of(price(service, invalidPrice))))))
          .as(invalidPrice)
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(
            () ->
                create(
                    with(
                        valid,
                        b ->
                            b.services(
                                List.of(
                                    AgreementConfiguration.ServicePrice.builder()
                                        .serviceId(service)
                                        .negotiatedPrice(null)
                                        .build())))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsDuplicateMissingOrInactiveServices() {
    UUID active = UUID.randomUUID(), inactive = UUID.randomUUID(), missing = UUID.randomUUID();
    when(catalog.findByIds(Set.of(active)))
        .thenReturn(List.of(catalogService(active, true, "200")));
    when(catalog.findByIds(Set.of(inactive)))
        .thenReturn(List.of(catalogService(inactive, false, "200")));
    when(catalog.findByIds(Set.of(missing))).thenReturn(List.of());

    assertThatThrownBy(() -> create(configuration(active, active)))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> create(configuration(inactive)))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> create(configuration(missing)))
        .isInstanceOf(DomainRuleViolation.class);
  }

  @Test
  void updateKeepsIdentifiersSnapshotsAndActiveFlagOfKeptServicesAndAddsNewOnes() {
    UUID kept = UUID.randomUUID(), added = UUID.randomUUID();
    var current = draftAgreement(UUID.randomUUID(), agreementId.value(), 4, kept);
    var keptService = current.services().getFirst();
    var withVersion =
        new HealthExaminationAgreementService(
            keptService.id(),
            keptService.serviceId(),
            agreementId,
            keptService.referencePriceSnapshot(),
            keptService.negotiatedPrice(),
            1,
            true,
            3);
    var stored =
        HealthExaminationAgreement.restoreConfiguration(
            agreementId,
            current.organizationId(),
            current.code(),
            current.name(),
            current.validFrom(),
            current.validTo(),
            List.of(withVersion),
            current.status(),
            current.rowVersion(),
            null);
    when(catalog.findByIds(Set.of(added))).thenReturn(List.of(catalogService(added, true, "500")));
    var replacement =
        AgreementConfiguration.builder()
            .agreementName("Renamed")
            .validFrom(null)
            .validTo(SECOND_DAY)
            .services(List.of(price(added, "70"), price(kept, "50")))
            .build();

    var assembled = update(stored, replacement);

    assertThat(assembled.name()).isEqualTo("Renamed");
    assertThat(assembled.validFrom()).isNull();
    assertThat(assembled.validTo()).isEqualTo(SECOND_DAY);
    var newService = assembled.services().getFirst();
    var retained = assembled.services().getLast();
    assertThat(newService.serviceId().value()).isEqualTo(added);
    assertThat(newService.referencePriceSnapshot().amount()).isEqualByComparingTo("500");
    assertThat(newService.displayOrder()).isEqualTo(1);
    assertThat(newService.rowVersion()).isZero();
    assertThat(retained.id()).isEqualTo(keptService.id());
    assertThat(retained.referencePriceSnapshot().amount()).isEqualByComparingTo("200");
    assertThat(retained.negotiatedPrice().amount()).isEqualByComparingTo("50");
    assertThat(retained.displayOrder()).isEqualTo(2);
    assertThat(retained.rowVersion()).isEqualTo(3);
    // Only additions use current catalog prices; display lookups do not replace stored snapshots.
    verify(catalog).findByIds(Set.of(added));
  }

  @Test
  void updateCarriesRetiredLinesAsHistoryAfterTheRequestedServices() {
    UUID kept = UUID.randomUUID();
    var current = draftAgreement(UUID.randomUUID(), agreementId.value(), 4, kept);
    var line = current.services().getFirst();
    var retired =
        new HealthExaminationAgreementService(
            new AggregateId(UUID.randomUUID()),
            new AggregateId(UUID.randomUUID()),
            agreementId,
            line.referencePriceSnapshot(),
            line.negotiatedPrice(),
            2,
            false,
            1);
    var stored =
        HealthExaminationAgreement.restoreConfiguration(
            agreementId,
            current.organizationId(),
            current.code(),
            current.name(),
            current.validFrom(),
            current.validTo(),
            List.of(line, retired),
            current.status(),
            current.rowVersion(),
            null);

    var assembled =
        update(
            stored,
            AgreementConfiguration.builder()
                .agreementName("Renamed")
                .services(List.of(price(kept, "60")))
                .build());

    assertThat(assembled.services())
        .extracting(s -> s.id())
        .containsExactly(line.id(), retired.id());
    assertThat(assembled.service(retired.id()).active()).isFalse();
    assertThat(assembled.service(retired.id()).negotiatedPrice())
        .isEqualTo(retired.negotiatedPrice());
  }

  @Test
  void configurationCopiesItsListsDefensively() {
    var services = new java.util.ArrayList<>(List.of(price(UUID.randomUUID(), "1")));
    var configuration = AgreementConfiguration.builder().services(services).build();
    services.add(price(UUID.randomUUID(), "2"));

    assertThat(configuration.services()).hasSize(1);
    assertThatThrownBy(() -> configuration.services().add(price(UUID.randomUUID(), "3")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
