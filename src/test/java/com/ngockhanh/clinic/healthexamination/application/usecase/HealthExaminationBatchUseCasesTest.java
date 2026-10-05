package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.*;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.shared.exception.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class HealthExaminationBatchUseCasesTest {
  private UpdateHealthExaminationBatchCommand configuration(UUID service) {
    return UpdateHealthExaminationBatchCommand.builder()
        .batchCode("B1")
        .batchName("Batch")
        .examinationDates(List.of(LocalDate.of(2026, 10, 4)))
        .examinationSiteType("CLINIC")
        .examinationSiteName("Clinic")
        .examinationSiteAddress("Address")
        .services(
            List.of(
                UpdateHealthExaminationBatchCommand.ServicePrice.builder()
                    .serviceId(service)
                    .negotiatedPrice(BigDecimal.TEN)
                    .build()))
        .build();
  }

  @Test
  void unknownOrganizationCannotCreateBatch() {
    UUID org = UUID.randomUUID(), actor = UUID.randomUUID(), service = UUID.randomUUID();
    var organizations = mock(OrganizationRepository.class);
    var repo = mock(HealthExaminationBatchRepository.class);
    var catalog = mock(ServiceCatalogQuery.class);
    var audit = mock(AuditWriter.class);
    when(organizations.findById(new AggregateId(org))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new CreateHealthExaminationBatchUseCase(
                        organizations, repo, new BatchDraftEditor(catalog), audit)
                    .execute(
                        org,
                        CreateHealthExaminationBatchCommand.builder()
                            .createdBy(actor)
                            .configuration(configuration(service))
                            .build()))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(repo, catalog, audit);
  }

  @Test
  void inactiveOrganizationCannotCreateBatch() {
    UUID org = UUID.randomUUID(), actor = UUID.randomUUID(), service = UUID.randomUUID();
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
                    .execute(
                        org,
                        CreateHealthExaminationBatchCommand.builder()
                            .createdBy(actor)
                            .configuration(configuration(service))
                            .build()))
        .isInstanceOf(BusinessRuleException.class);
    verify(repo, never()).insert(any(), any());
  }
}
