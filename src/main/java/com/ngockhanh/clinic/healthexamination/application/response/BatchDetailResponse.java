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
    List<DayResponse> days,
    LocalDate startDate,
    LocalDate endDate,
    String examinationSiteType,
    String examinationSiteName,
    String examinationSiteAddress,
    String status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion,
    List<ServiceResponse> services) {
  public record DayResponse(UUID id, LocalDate examinationDate) {}

  public record ServiceResponse(
      UUID id,
      UUID serviceId,
      BigDecimal referencePriceSnapshot,
      BigDecimal negotiatedPrice,
      int displayOrder,
      boolean active,
      long rowVersion) {}

  public static BatchDetailResponse from(BatchDetails d) {
    var b = d.batch();
    return new BatchDetailResponse(
        b.id().value(),
        b.organizationId().value(),
        b.code(),
        b.name(),
        b.days().stream().map(dy -> new DayResponse(dy.id(), dy.examinationDate())).toList(),
        b.startDate(),
        b.endDate(),
        b.site().type().name(),
        b.site().name(),
        b.site().address(),
        b.status().name(),
        d.createdBy(),
        d.createdAt(),
        d.updatedAt(),
        b.rowVersion(),
        b.services().stream()
            .map(
                s ->
                    new ServiceResponse(
                        s.id().value(),
                        s.serviceId().value(),
                        s.referencePriceSnapshot().amount(),
                        s.negotiatedPrice().amount(),
                        s.displayOrder(),
                        s.active(),
                        s.rowVersion()))
            .toList());
  }

  public record AuditSnapshot(
      String batchCode,
      String batchName,
      List<DayResponse> days,
      String siteType,
      String siteName,
      String siteAddress,
      String status,
      List<ServiceResponse> services) {}

  public AuditSnapshot auditSummary() {
    return new AuditSnapshot(
        batchCode,
        batchName,
        days,
        examinationSiteType,
        examinationSiteName,
        examinationSiteAddress,
        status,
        services);
  }
}
