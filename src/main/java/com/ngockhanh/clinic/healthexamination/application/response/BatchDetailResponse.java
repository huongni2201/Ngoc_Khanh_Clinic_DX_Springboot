package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDetails;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record BatchDetailResponse(
    UUID id,
    UUID organizationId,
    String batchCode,
    String batchName,
    LocalDate startDate,
    LocalDate endDate,
    String reason,
    String payerType,
    String examinationSiteType,
    String examinationSiteName,
    String examinationSiteAddress,
    UUID masterTemplateVersionId,
    String status,
    Instant finalizedAt,
    Instant closedAt,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    List<ServiceResponse> services) {
  public record ServiceResponse(
      UUID id,
      UUID serviceId,
      String serviceCode,
      String serviceName,
      BigDecimal negotiatedUnitPrice,
      String currency,
      int displayOrder,
      String status,
      UUID documentTemplateVersionId) {}

  public static BatchDetailResponse from(BatchDetails d) {
    var b = d.batch();
    return new BatchDetailResponse(
        b.id().value(),
        b.organizationId().value(),
        b.code(),
        b.name(),
        b.startDate(),
        b.endDate(),
        b.reason(),
        b.payerType(),
        b.site().type().name(),
        b.site().name(),
        b.site().address(),
        b.masterTemplateVersionId().value(),
        b.status().name(),
        b.finalizedAt(),
        b.closedAt(),
        d.createdBy(),
        d.createdAt(),
        d.updatedAt(),
        b.services().stream()
            .map(
                s ->
                    new ServiceResponse(
                        s.id().value(),
                        s.serviceId().value(),
                        s.serviceCode(),
                        s.serviceName(),
                        s.negotiatedPrice().amount(),
                        s.negotiatedPrice().currency(),
                        s.displayOrder(),
                        s.status(),
                        s.templateVersionId() == null ? null : s.templateVersionId().value()))
            .toList());
  }

  public record AuditSnapshot(
      String batchCode,
      String batchName,
      LocalDate startDate,
      LocalDate endDate,
      String reason,
      String payerType,
      String siteType,
      String siteName,
      String siteAddress,
      String status,
      List<ServiceResponse> services) {}

  public AuditSnapshot auditSummary() {
    return new AuditSnapshot(
        batchCode,
        batchName,
        startDate,
        endDate,
        reason,
        payerType,
        examinationSiteType,
        examinationSiteName,
        examinationSiteAddress,
        status,
        services);
  }
}
