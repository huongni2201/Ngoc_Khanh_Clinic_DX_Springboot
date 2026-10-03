package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.document.application.query.MasterHealthExaminationTemplateQuery;
import com.ngockhanh.clinic.healthexamination.application.command.*;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.*;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDetails;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.shared.audit.AuditWriter;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HealthExaminationBatchUseCasesTest {
  UUID org = UUID.randomUUID(),
      batchId = UUID.randomUUID(),
      service = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      template = UUID.randomUUID();

  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final AuditWriter audit = mock(AuditWriter.class);

  BatchConfigurationCommand config() {
    return new BatchConfigurationCommand(
        "B01",
        "Campaign",
        null,
        null,
        null,
        null,
        "CLINIC",
        "Clinic",
        null,
        List.of(new BatchConfigurationCommand.ServicePrice(service, BigDecimal.TEN)));
  }

  HealthExaminationBatch batch() {
    return HealthExaminationBatch.createDraft(
        new AggregateId(batchId),
        new AggregateId(org),
        "B01",
        "Campaign",
        null,
        null,
        null,
        null,
        new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null),
        new AggregateId(template),
        List.of(
            HealthExaminationBatchService.create(
                new AggregateId(UUID.randomUUID()),
                new AggregateId(service),
                new AggregateId(batchId),
                "S1",
                "Original",
                Money.vnd("9"),
                null,
                1,
                "ACTIVE")));
  }

  @Test
  void updatePreservesSnapshotAndServiceIdentityAndAuditsBothPrices() {
    var batch = batch();
    var row = batch.services().getFirst().id();
    var details = new BatchDetails(batch, actor, null, null);
    when(batches.findDetails(org, batchId, true)).thenReturn(Optional.of(details));
    when(batches.findDetails(org, batchId, false)).thenReturn(Optional.of(details));
    when(catalog.findByIds(Set.of(service)))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(service, "NEW", "Renamed", true, true)));
    var result =
        new UpdateHealthExaminationBatchUseCase(batches, new BatchDraftEditor(catalog), audit)
            .execute(org, batchId, config(), actor);
    assertThat(result.services().getFirst().id()).isEqualTo(row.value());
    assertThat(result.services().getFirst().serviceName()).isEqualTo("Original");
    assertThat(result.services().getFirst().negotiatedUnitPrice()).isEqualByComparingTo("10");
    var before = ArgumentCaptor.forClass(BatchDetailResponse.AuditSnapshot.class);
    var after = ArgumentCaptor.forClass(BatchDetailResponse.AuditSnapshot.class);
    verify(audit)
        .record(
            eq(actor),
            eq("UPDATE_HEALTH_EXAMINATION_BATCH"),
            eq("HEALTH_EXAMINATION_BATCH"),
            eq(batchId),
            before.capture(),
            after.capture());
    assertThat(before.getValue().services().getFirst().negotiatedUnitPrice())
        .isEqualByComparingTo("9");
    assertThat(after.getValue().services().getFirst().negotiatedUnitPrice())
        .isEqualByComparingTo("10");
  }

  @Test
  void deletionIsIdempotentAndHiddenFromDetail() {
    var batch = batch();
    var details = new BatchDetails(batch, actor, null, null);
    when(batches.findDetailsIncludingDeleted(org, batchId, true)).thenReturn(Optional.of(details));
    var delete = new DeleteHealthExaminationBatchUseCase(batches, audit);
    delete.execute(org, batchId, actor);
    delete.execute(org, batchId, actor);
    verify(batches, times(1)).update(batch);
    verify(audit, times(1)).record(any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> new GetHealthExaminationBatchUseCase(batches).execute(org, batchId))
        .isInstanceOf(com.ngockhanh.clinic.shared.exception.ResourceNotFoundException.class);
  }

  @Test
  void refusesDeletionOfDependentOrReadyBatch() {
    var batch = batch();
    when(batches.findDetailsIncludingDeleted(org, batchId, true))
        .thenReturn(Optional.of(new BatchDetails(batch, actor, null, null)));
    when(batches.hasDependents(batchId)).thenReturn(true);
    assertThatThrownBy(
            () ->
                new DeleteHealthExaminationBatchUseCase(batches, audit)
                    .execute(org, batchId, actor))
        .isInstanceOf(RuntimeException.class);
    when(batches.hasDependents(batchId)).thenReturn(false);
    batch.markReady();
    assertThatThrownBy(
            () ->
                new DeleteHealthExaminationBatchUseCase(batches, audit)
                    .execute(org, batchId, actor))
        .isInstanceOf(RuntimeException.class);
    verify(batches, never()).update(any());
    verifyNoInteractions(audit);
  }

  @Test
  void listUsesOneBasedOffsetsAndTreatsWildcardsLiterally() {
    when(organizations.findById(new AggregateId(org)))
        .thenReturn(
            Optional.of(Organization.create(new AggregateId(org), "Org", "Contact", "0900")));
    when(batches.findPage(any(), anyLong(), anyInt(), any(), any(), any())).thenReturn(List.of());
    var query = new HealthExaminationBatchListQuery(2, 10, "%_", "batchCode", "desc");
    var result = new ListHealthExaminationBatchUseCase(organizations, batches).execute(org, query);
    assertThat(result.page()).isEqualTo(2);
    verify(batches).findPage(eq(org), eq(10L), eq(10), eq("%\\%\\_%"), eq("batchCode"), eq("DESC"));
    assertThatThrownBy(
            () ->
                new ListHealthExaminationBatchUseCase(organizations, batches)
                    .execute(org, new HealthExaminationBatchListQuery(0, 10, null, "id", "ASC")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsInactiveOrganizationWithoutPersisting() {
    when(organizations.findById(new AggregateId(org)))
        .thenReturn(
            Optional.of(
                Organization.create(new AggregateId(org), "Org", "Contact", "0900").deactivate()));
    var templates = mock(MasterHealthExaminationTemplateQuery.class);
    var create =
        new CreateHealthExaminationBatchUseCase(
            organizations, batches, new BatchDraftEditor(catalog), templates, audit);
    assertThatThrownBy(
            () -> create.execute(org, new CreateHealthExaminationBatchCommand(actor, config())))
        .isInstanceOf(RuntimeException.class);
    verifyNoInteractions(batches, catalog, templates, audit);
  }
}
