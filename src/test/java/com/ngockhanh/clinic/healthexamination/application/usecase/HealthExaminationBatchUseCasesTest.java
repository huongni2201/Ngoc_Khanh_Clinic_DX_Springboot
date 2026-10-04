package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.*;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.*;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.integration.application.imports.ImportStore;
import com.ngockhanh.clinic.shared.exception.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class HealthExaminationBatchUseCasesTest {
  private final UUID org = UUID.randomUUID(),
      id = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      service = UUID.randomUUID();
  private final BatchDay day = new BatchDay(UUID.randomUUID(), LocalDate.of(2026, 10, 4));

  private HealthExaminationBatch batch() {
    return HealthExaminationBatch.restoreConfiguration(
        new AggregateId(id),
        new AggregateId(org),
        "B1",
        "Batch",
        new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", "Address"),
        List.of(day),
        List.of(
            new HealthExaminationBatchService(
                new AggregateId(UUID.randomUUID()),
                new AggregateId(service),
                new AggregateId(id),
                Money.vnd("200"),
                Money.vnd("100"),
                1,
                true,
                0)),
        BatchStatus.DRAFT,
        3);
  }

  private BatchConfigurationCommand command(Long version) {
    return new BatchConfigurationCommand(
        "B2",
        "Renamed",
        List.of(day.examinationDate()),
        "CLINIC",
        "Clinic",
        "Address",
        List.of(new BatchConfigurationCommand.ServicePrice(service, BigDecimal.TEN)),
        version);
  }

  @Test
  void staleEditIsRejectedBeforeCatalogWriteAndAudit() {
    var repo = mock(HealthExaminationBatchRepository.class);
    var catalog = mock(ServiceCatalogQuery.class);
    var audit = mock(AuditWriter.class);
    when(repo.findDetails(org, id, true))
        .thenReturn(Optional.of(new BatchDetails(batch(), actor, null, null)));
    assertThatThrownBy(
            () ->
                new UpdateHealthExaminationBatchUseCase(
                        repo, new BatchDraftEditor(catalog), audit, mock(ImportStore.class))
                    .execute(org, id, command(2L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(repo, never()).update(any());
    verifyNoInteractions(catalog, audit);
  }

  @Test
  void retainedDayAndReferencePriceSurviveUpdate() {
    var repo = mock(HealthExaminationBatchRepository.class);
    var catalog = mock(ServiceCatalogQuery.class);
    var audit = mock(AuditWriter.class);
    var batch = batch();
    var details = new BatchDetails(batch, actor, null, null);
    when(repo.findDetails(org, id, true)).thenReturn(Optional.of(details));
    when(repo.findDetails(org, id, false)).thenReturn(Optional.of(details));
    new UpdateHealthExaminationBatchUseCase(
            repo, new BatchDraftEditor(catalog), audit, mock(ImportStore.class))
        .execute(org, id, command(3L), actor);
    verify(repo).update(batch);
    assertThat(batch.days()).containsExactly(day);
    assertThat(batch.services().getFirst().referencePriceSnapshot().amount())
        .isEqualByComparingTo("200");
    verify(audit)
        .record(
            eq(actor),
            eq("UPDATE_HEALTH_EXAMINATION_BATCH"),
            eq("HEALTH_EXAMINATION_BATCH"),
            eq(id),
            any(),
            any());
  }

  @Test
  void stagedImportReferenceRejectsDayRemovalBeforeMutationAndAudit() {
    var repo = mock(HealthExaminationBatchRepository.class);
    var catalog = mock(ServiceCatalogQuery.class);
    var audit = mock(AuditWriter.class);
    var imports = mock(ImportStore.class);
    var batch = batch();
    when(repo.findDetails(org, id, true))
        .thenReturn(Optional.of(new BatchDetails(batch, actor, null, null)));
    when(imports.hasBatchDayReferences(id, List.of(day.id()))).thenReturn(true);
    var desired =
        new BatchConfigurationCommand(
            "B2",
            "Renamed",
            List.of(day.examinationDate().plusDays(1)),
            "CLINIC",
            "Clinic",
            "Address",
            List.of(new BatchConfigurationCommand.ServicePrice(service, BigDecimal.TEN)),
            3L);
    assertThatThrownBy(
            () ->
                new UpdateHealthExaminationBatchUseCase(
                        repo, new BatchDraftEditor(catalog), audit, imports)
                    .execute(org, id, desired, actor))
        .isInstanceOf(BusinessRuleException.class);
    verify(imports).hasBatchDayReferences(id, List.of(day.id()));
    verify(repo, never()).update(any());
    verifyNoInteractions(catalog, audit);
    assertThat(batch.days()).containsExactly(day);
  }

  @Test
  void inactiveOrganizationCannotCreateBatch() {
    var organizations = mock(OrganizationRepository.class);
    var repo = mock(HealthExaminationBatchRepository.class);
    var catalog = mock(ServiceCatalogQuery.class);
    var audit = mock(AuditWriter.class);
    var organization =
        Organization.create(
                new AggregateId(org),
                "O1",
                "Org",
                "COMPANY",
                null,
                "0901",
                "o@example.test",
                "Address",
                "Contact",
                null,
                "0902",
                "c@example.test")
            .deactivate();
    when(organizations.findById(new AggregateId(org))).thenReturn(Optional.of(organization));
    assertThatThrownBy(
            () ->
                new CreateHealthExaminationBatchUseCase(
                        organizations, repo, new BatchDraftEditor(catalog), audit)
                    .execute(org, new CreateHealthExaminationBatchCommand(actor, command(null))))
        .isInstanceOf(BusinessRuleException.class);
    verify(repo, never()).insert(any(), any());
  }
}
