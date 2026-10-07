package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateHealthExaminationBatchUseCaseTest {
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final CreateHealthExaminationBatchUseCase useCase =
      new CreateHealthExaminationBatchUseCase(organizations, batches, catalog, audit);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID service = UUID.randomUUID();

  @BeforeEach
  void storedBatchIsReadBackWithDatabaseVersionAndTimestamps() {
    when(batches.findDetails(eq(organizationId), any(), eq(false)))
        .thenAnswer(
            call -> {
              var captor = ArgumentCaptor.forClass(HealthExaminationBatch.class);
              verify(batches).insert(captor.capture(), eq(actor));
              return Optional.of(details(captor.getValue()));
            });
  }

  private static CreateHealthExaminationBatchCommand command(
      com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration config) {
    return CreateHealthExaminationBatchCommand.builder().configuration(config).build();
  }

  @Test
  void createsADraftWithSnapshotsAndAuditsInTheSameFlow() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(
            List.of(
                new ServiceCatalogQuery.Service(
                    service, "S1", "Exam", true, new BigDecimal("200"))));

    var response = useCase.execute(organizationId, command(configuration(service)), actor);

    var inserted = ArgumentCaptor.forClass(HealthExaminationBatch.class);
    var order = inOrder(batches, audit);
    order.verify(batches).insert(inserted.capture(), eq(actor));
    var batch = inserted.getValue();
    assertThat(batch.status()).isEqualTo(BatchStatus.DRAFT);
    assertThat(batch.rowVersion()).isZero();
    assertThat(batch.organizationId().value()).isEqualTo(organizationId);
    assertThat(batch.id().value().version()).as("UUIDv7").isEqualTo(7);
    assertThat(batch.services().getFirst().referencePriceSnapshot().amount())
        .isEqualByComparingTo("200");
    assertThat(batch.services().getFirst().negotiatedPrice().amount()).isEqualByComparingTo("100");
    assertThat(response.id()).isEqualTo(batch.id().value());
    assertThat(response.status()).isEqualTo("DRAFT");
    assertThat(response.rowVersion()).isZero();
    assertThat(response.createdAt()).isEqualTo(CREATED_AT);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
    order
        .verify(audit)
        .record(
            eq(actor),
            eq("CREATE_HEALTH_EXAMINATION_BATCH"),
            eq("HEALTH_EXAMINATION_BATCH"),
            eq(batch.id().value()),
            eq(null),
            after.capture());
    assertThat(after.getValue())
        .containsEntry("organizationId", organizationId)
        .containsEntry("status", "DRAFT")
        .containsEntry("rowVersion", 0L)
        .containsKey("configuration");
  }

  @Test
  void unknownOrganizationIsNotFoundAndNothingIsWritten() {
    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> useCase.execute(organizationId, command(configuration(service)), actor))
        .isInstanceOf(ResourceNotFoundException.class);

    verifyNoInteractions(batches, catalog, audit);
  }

  @Test
  void inactiveOrganizationCannotReceiveANewBatch() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));

    assertThatThrownBy(
            () -> useCase.execute(organizationId, command(configuration(service)), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verifyNoInteractions(batches, catalog, audit);
  }

  @Test
  void unavailableServiceIsRejectedBeforeAnythingIsWritten() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(service, "S1", "Exam", false, BigDecimal.TEN)));

    assertThatThrownBy(
            () -> useCase.execute(organizationId, command(configuration(service)), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(batches, never()).insert(any(), any());
    verifyNoInteractions(audit);
  }

  @Test
  void invalidConfigurationIsRejectedBeforeAnythingIsWritten() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    var duplicateDates =
        configuration(service).toBuilder().examinationDates(List.of(FIRST_DAY, FIRST_DAY)).build();

    assertThatThrownBy(() -> useCase.execute(organizationId, command(duplicateDates), actor))
        .isInstanceOf(IllegalArgumentException.class);

    verify(batches, never()).insert(any(), any());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsMissingArguments() {
    assertThatThrownBy(() -> useCase.execute(null, command(configuration(service)), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, null, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, command(null), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, command(configuration(service)), null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(organizations, batches, audit);
  }

  @Test
  void auditFailureIsNotSwallowedSoTheTransactionRollsBack() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(service, "S1", "Exam", true, BigDecimal.TEN)));
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(
            () -> useCase.execute(organizationId, command(configuration(service)), actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("audit unavailable");
  }

  @Test
  void aDateInThePastIsNotRejected() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(service, "S1", "Exam", true, BigDecimal.TEN)));
    var past =
        configuration(service).toBuilder()
            .examinationDates(List.of(LocalDate.of(2001, 1, 1)))
            .build();

    assertThat(useCase.execute(organizationId, command(past), actor).startDate())
        .isEqualTo(LocalDate.of(2001, 1, 1));
  }
}
