package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchConfigurationUseCaseTest {
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final CreateHealthExaminationBatchUseCase create =
      new CreateHealthExaminationBatchUseCase(organizations, batches, catalog, audit);
  private final UpdateHealthExaminationBatchUseCase update =
      new UpdateHealthExaminationBatchUseCase(organizations, batches, catalog, audit);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();

  private HealthExaminationBatch create(BatchConfiguration configuration) {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    var inserted = ArgumentCaptor.forClass(HealthExaminationBatch.class);
    when(batches.findDetails(eq(organizationId), any(), eq(false)))
        .thenAnswer(
            call -> {
              verify(batches).insert(inserted.capture(), eq(actor));
              return Optional.of(details(inserted.getValue()));
            });
    create.execute(
        organizationId,
        CreateHealthExaminationBatchCommand.builder().configuration(configuration).build(),
        actor);
    return inserted.getValue();
  }

  private HealthExaminationBatch update(
      HealthExaminationBatch current, BatchConfiguration configuration) {
    var owner = current.organizationId().value();
    var id = current.id().value();
    when(organizations.findById(current.organizationId()))
        .thenReturn(Optional.of(organization(owner)));
    when(batches.findDetails(owner, id, true)).thenReturn(Optional.of(details(current)));
    when(batches.findDetails(owner, id, false)).thenReturn(Optional.of(details(current)));
    update.execute(
        owner,
        id,
        UpdateHealthExaminationBatchCommand.builder()
            .configuration(configuration)
            .rowVersion(current.rowVersion())
            .build(),
        actor);
    verify(batches).update(current, current.rowVersion());
    return current;
  }

  private final AggregateId batchId = new AggregateId(UUID.randomUUID());

  private static ServiceCatalogQuery.Service catalogService(UUID id, boolean active, String price) {
    return new ServiceCatalogQuery.Service(id, "S", "Exam", active, new BigDecimal(price));
  }

  private static BatchConfiguration with(
      BatchConfiguration base,
      java.util.function.UnaryOperator<BatchConfiguration.BatchConfigurationBuilder> change) {
    return change.apply(base.toBuilder()).build();
  }

  private static BatchConfiguration.ServicePrice price(UUID service, String price) {
    return BatchConfiguration.ServicePrice.builder()
        .serviceId(service)
        .negotiatedPrice(new BigDecimal(price))
        .build();
  }

  @Test
  void createsDaysAndServicesWithNewIdsAndCatalogPriceSnapshots() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    when(catalog.findByIds(Set.of(first, second)))
        .thenReturn(
            List.of(catalogService(first, true, "200"), catalogService(second, true, "300")));
    var configuration =
        with(configuration(first, second), b -> b.examinationDates(List.of(SECOND_DAY, FIRST_DAY)));

    var assembled = create(configuration);

    assertThat(assembled.code()).isEqualTo("B1");
    assertThat(assembled.site().type()).isEqualTo(ExaminationSiteType.CLINIC);
    assertThat(assembled.days())
        .extracting(HealthExaminationBatchDay::examinationDate)
        .containsExactly(FIRST_DAY, SECOND_DAY);
    assertThat(assembled.days()).extracting(HealthExaminationBatchDay::id).doesNotHaveDuplicates();
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
        .extracting(HealthExaminationBatchService::displayOrder)
        .containsExactly(1, 2);
    assertThat(assembled.services())
        .allSatisfy(
            s -> {
              assertThat(s.batchId()).isEqualTo(assembled.id());
              assertThat(s.rowVersion()).isZero();
              assertThat(s.active()).isTrue();
            });
  }

  @Test
  void rejectsInvalidDatesSiteAndPrices() {
    UUID service = UUID.randomUUID();
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(List.of(catalogService(service, true, "200")));
    var valid = configuration(service);

    assertThatThrownBy(
            () -> create(with(valid, b -> b.examinationDates(List.of(FIRST_DAY, FIRST_DAY)))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(with(valid, b -> b.examinationDates(List.of()))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(with(valid, b -> b.examinationDates(null))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                create(
                    with(
                        valid,
                        b ->
                            b.examinationDates(
                                java.util.Arrays.asList(FIRST_DAY, (LocalDate) null)))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(with(valid, b -> b.examinationSiteType("COMPANY"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(with(valid, b -> b.examinationSiteType(null))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create(with(valid, b -> b.services(List.of()))))
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
                                    BatchConfiguration.ServicePrice.builder()
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
  void updateKeepsIdentifiersSnapshotsAndActiveFlagOfKeptDaysAndServices() {
    UUID kept = UUID.randomUUID(), added = UUID.randomUUID();
    var current = draftBatch(UUID.randomUUID(), batchId.value(), 4, kept);
    var keptService = current.services().getFirst();
    var keptDay =
        current.days().stream()
            .filter(d -> d.examinationDate().equals(SECOND_DAY))
            .findFirst()
            .orElseThrow();
    var withVersion =
        new HealthExaminationBatchService(
            keptService.id(),
            keptService.serviceId(),
            batchId,
            keptService.referencePriceSnapshot(),
            keptService.negotiatedPrice(),
            1,
            false,
            3);
    var stored =
        com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch
            .restoreConfiguration(
                batchId,
                current.organizationId(),
                current.code(),
                current.name(),
                current.site(),
                current.days(),
                List.of(withVersion),
                current.status(),
                current.rowVersion());
    when(catalog.findByIds(Set.of(added))).thenReturn(List.of(catalogService(added, true, "500")));
    var replacement =
        BatchConfiguration.builder()
            .batchCode("B2")
            .batchName("Renamed")
            .examinationDates(List.of(SECOND_DAY, LocalDate.of(2026, 10, 6)))
            .examinationSiteType("ORGANIZATION_SITE")
            .examinationSiteName("Site")
            .examinationSiteAddress("Elsewhere")
            .services(List.of(price(added, "70"), price(kept, "50")))
            .build();

    var previousDayIds = stored.days().stream().map(HealthExaminationBatchDay::id).toList();
    var assembled = update(stored, replacement);

    assertThat(assembled.days())
        .extracting(HealthExaminationBatchDay::examinationDate)
        .containsExactly(SECOND_DAY, LocalDate.of(2026, 10, 6));
    assertThat(assembled.days().getFirst().id()).isEqualTo(keptDay.id());
    assertThat(assembled.days().getLast().id()).isNotIn(previousDayIds);
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
    assertThat(retained.active()).isFalse();
    assertThat(retained.rowVersion()).isEqualTo(3);
    // Only additions use current catalog prices; display lookups do not replace stored snapshots.
    verify(catalog).findByIds(Set.of(added));
  }

  @Test
  void updateWithOnlyKeptServicesUsesCatalogOnlyForDisplay() {
    UUID kept = UUID.randomUUID();
    var current = draftBatch(UUID.randomUUID(), batchId.value(), 0, kept);

    var assembled = update(current, configuration(kept));

    assertThat(assembled.services().getFirst().id()).isEqualTo(current.services().getFirst().id());
    verify(catalog, org.mockito.Mockito.times(2)).findByIds(Set.of(kept));
  }

  @Test
  void configurationCopiesItsListsDefensively() {
    var dates = new java.util.ArrayList<>(List.of(FIRST_DAY));
    var configuration =
        BatchConfiguration.builder().examinationDates(dates).services(List.of()).build();
    dates.add(SECOND_DAY);

    assertThat(configuration.examinationDates()).containsExactly(FIRST_DAY);
    assertThatThrownBy(() -> configuration.examinationDates().add(SECOND_DAY))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
