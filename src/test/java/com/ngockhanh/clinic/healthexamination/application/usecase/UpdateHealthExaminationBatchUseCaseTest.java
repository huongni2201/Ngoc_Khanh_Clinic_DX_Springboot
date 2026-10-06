package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.service.BatchConfigurationAssembler;
import com.ngockhanh.clinic.healthexamination.application.service.BatchDetailResponseMapper;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
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

class UpdateHealthExaminationBatchUseCaseTest {
  private static final LocalDate THIRD_DAY = LocalDate.of(2026, 10, 6);

  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final ServiceCatalogQuery displayCatalog = mock(ServiceCatalogQuery.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final UpdateHealthExaminationBatchUseCase useCase =
      new UpdateHealthExaminationBatchUseCase(
          organizations,
          batches,
          new BatchConfigurationAssembler(catalog),
          new BatchDetailResponseMapper(displayCatalog),
          audit);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID kept = UUID.randomUUID();
  private final UUID dropped = UUID.randomUUID();
  private final UUID added = UUID.randomUUID();
  private HealthExaminationBatch current;

  @BeforeEach
  void aDraftWithVersionThreeAndTwoServices() {
    current = draftBatch(organizationId, batchId, 3, kept, dropped);
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(batches.findDetails(organizationId, batchId, true))
        .thenReturn(Optional.of(details(current)));
  }

  private UpdateHealthExaminationBatchCommand command(BatchConfiguration config, Long version) {
    return UpdateHealthExaminationBatchCommand.builder()
        .configuration(config)
        .rowVersion(version)
        .build();
  }

  private BatchConfiguration replacement(UUID... services) {
    return configuration(services).toBuilder()
        .batchCode("B2")
        .examinationDates(List.of(SECOND_DAY, THIRD_DAY))
        .build();
  }

  /** The row as the database would return it after the header version was incremented. */
  private void storedAfterUpdate() {
    when(batches.findDetails(organizationId, batchId, false))
        .thenAnswer(
            call -> {
              var updated = ArgumentCaptor.forClass(HealthExaminationBatch.class);
              verify(batches).update(updated.capture(), anyLong());
              var b = updated.getValue();
              return Optional.of(
                  details(
                      HealthExaminationBatch.restoreConfiguration(
                          b.id(),
                          b.organizationId(),
                          b.code(),
                          b.name(),
                          b.site(),
                          b.days(),
                          b.services(),
                          b.status(),
                          b.rowVersion() + 1)));
            });
  }

  @Test
  void replacesTheConfigurationKeepingIdsAndAuditsBeforeAndAfter() {
    var keptService = current.services().getFirst();
    var keptDay =
        current.days().stream()
            .filter(d -> d.examinationDate().equals(SECOND_DAY))
            .findFirst()
            .orElseThrow();
    var removedDay =
        current.days().stream()
            .filter(d -> d.examinationDate().equals(FIRST_DAY))
            .findFirst()
            .orElseThrow();
    var removedService = current.services().getLast();
    when(catalog.findByIds(Set.of(added)))
        .thenReturn(
            List.of(
                new ServiceCatalogQuery.Service(added, "S2", "New", true, new BigDecimal("500"))));
    storedAfterUpdate();

    var response = useCase.execute(organizationId, batchId, command(replacement(added, kept), 3L), actor);

    var updated = ArgumentCaptor.forClass(HealthExaminationBatch.class);
    var order = inOrder(batches, audit);
    order.verify(batches).findReferencedDayIds(eq(batchId), eq(Set.of(removedDay.id())));
    order.verify(batches).findReferencedBatchServiceIds(eq(batchId), eq(Set.of(removedService.id().value())));
    order.verify(batches).update(updated.capture(), eq(3L));
    assertThat(updated.getValue().code()).isEqualTo("B2");
    assertThat(updated.getValue().days())
        .extracting(HealthExaminationBatchDay::examinationDate)
        .containsExactly(SECOND_DAY, THIRD_DAY);
    assertThat(updated.getValue().days().getFirst().id()).isEqualTo(keptDay.id());
    assertThat(updated.getValue().services())
        .extracting(s -> s.serviceId().value())
        .containsExactly(added, kept);
    assertThat(updated.getValue().services().getLast().id()).isEqualTo(keptService.id());
    assertThat(response.rowVersion()).isEqualTo(4);
    assertThat(response.batchCode()).isEqualTo("B2");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
    order
        .verify(audit)
        .record(
            eq(actor),
            eq("UPDATE_HEALTH_EXAMINATION_BATCH"),
            eq("HEALTH_EXAMINATION_BATCH"),
            eq(batchId),
            before.capture(),
            after.capture());
    assertThat(before.getValue()).containsEntry("rowVersion", 3L).containsEntry("status", "DRAFT");
    assertThat(after.getValue()).containsEntry("rowVersion", 4L).containsEntry("status", "DRAFT");
  }

  @Test
  void aPutWithTheSameConfigurationStillStoresOnceSoTheVersionIsIncremented() {
    storedAfterUpdate();
    var same =
        configuration(kept, dropped).toBuilder()
            .examinationDates(List.of(FIRST_DAY, SECOND_DAY))
            .build();

    var response = useCase.execute(organizationId, batchId, command(same, 3L), actor);

    verify(batches).update(any(), eq(3L));
    assertThat(response.rowVersion()).isEqualTo(4);
    verifyNoInteractions(catalog);
  }

  @Test
  void aStaleVersionIsAConflictAndNothingIsChanged() {
    assertThatThrownBy(
            () -> useCase.execute(organizationId, batchId, command(replacement(kept), 2L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verify(batches, never()).update(any(), anyLong());
    verifyNoInteractions(audit, catalog);
  }

  @Test
  void aBatchThatIsNotADraftCannotBeUpdated() {
    for (BatchStatus status : List.of(BatchStatus.READY, BatchStatus.FINALIZED, BatchStatus.CLOSED)) {
      when(batches.findDetails(organizationId, batchId, true))
          .thenReturn(
              Optional.of(details(batch(organizationId, batchId, status, 3, null, kept, dropped))));

      assertThatThrownBy(
              () -> useCase.execute(organizationId, batchId, command(replacement(kept), 3L), actor))
          .as(status.name())
          .isInstanceOf(DomainRuleViolation.class);
    }
    verify(batches, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void removingADayThatHasParticipantsIsRejected() {
    when(batches.findReferencedDayIds(eq(batchId), any()))
        .thenAnswer(call -> Set.copyOf((java.util.Collection<UUID>) call.getArgument(1)));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, batchId, command(replacement(kept, dropped), 3L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(batches, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void removingAServiceThatHasParticipantServicesIsRejectedEvenIfNeverPerformed() {
    when(batches.findReferencedBatchServiceIds(eq(batchId), any()))
        .thenAnswer(call -> Set.copyOf((java.util.Collection<UUID>) call.getArgument(1)));
    var keepsDaysDropsService =
        configuration(kept).toBuilder().examinationDates(List.of(FIRST_DAY, SECOND_DAY)).build();

    assertThatThrownBy(
            () -> useCase.execute(organizationId, batchId, command(keepsDaysDropsService, 3L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(batches, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void aConflictDetectedWhileStoringIsNotAuditedAndPropagates() {
    doThrow(new ConcurrentUpdateException()).when(batches).update(any(), anyLong());
    var same =
        configuration(kept, dropped).toBuilder()
            .examinationDates(List.of(FIRST_DAY, SECOND_DAY))
            .build();

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(same, 3L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);

    verifyNoInteractions(audit);
  }

  @Test
  void auditFailureIsNotSwallowedSoTheTransactionRollsBack() {
    storedAfterUpdate();
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());
    var same =
        configuration(kept, dropped).toBuilder()
            .examinationDates(List.of(FIRST_DAY, SECOND_DAY))
            .build();

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(same, 3L), actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("audit unavailable");
  }

  @Test
  void aNewServiceMustBeActiveInTheCatalogButAKeptOneIsNotRechecked() {
    when(catalog.findByIds(Set.of(added)))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(added, "S2", "New", false, BigDecimal.TEN)));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, batchId, command(replacement(kept, added), 3L), actor))
        .isInstanceOf(DomainRuleViolation.class);

    verify(batches, never()).update(any(), anyLong());
  }

  @Test
  void unknownOrganizationOrBatchIsNotFoundAndAnInactiveOrganizationIsAllowed() {
    storedAfterUpdate();
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));
    var same =
        configuration(kept, dropped).toBuilder()
            .examinationDates(List.of(FIRST_DAY, SECOND_DAY))
            .build();
    assertThat(useCase.execute(organizationId, batchId, command(same, 3L), actor).rowVersion())
        .isEqualTo(4);

    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(same, 3L), actor))
        .isInstanceOf(ResourceNotFoundException.class);

    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(same, 3L), actor))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void rejectsMissingArgumentsAndInvalidVersions() {
    var config = replacement(kept);
    assertThatThrownBy(() -> useCase.execute(null, batchId, command(config, 3L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, null, command(config, 3L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, null, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(null, 3L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(config, 3L), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(config, null), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(config, -1L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    verify(batches, never()).update(any(), anyLong());
  }
}
