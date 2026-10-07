package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDetails;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.Builder;

@Builder
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
  @Builder
  public record DayResponse(UUID id, LocalDate examinationDate) {}

  @Builder
  public record ServiceResponse(
      UUID id,
      UUID serviceId,
      String serviceCode,
      String serviceName,
      BigDecimal referencePriceSnapshot,
      BigDecimal negotiatedPrice,
      int displayOrder,
      boolean active,
      long rowVersion) {}

  /**
   * Maps stored batch details to the response.
   *
   * @param catalog catalog services keyed by id; a service missing from it gets a {@code null} code
   *     and name
   */
  public static BatchDetailResponse from(
      BatchDetails d, Map<UUID, ServiceCatalogQuery.Service> catalog) {
    var b = d.batch();
    return BatchDetailResponse.builder()
        .id(b.id().value())
        .organizationId(b.organizationId().value())
        .batchCode(b.code())
        .batchName(b.name())
        .days(
            b.days().stream()
                .map(
                    dy ->
                        DayResponse.builder()
                            .id(dy.id())
                            .examinationDate(dy.examinationDate())
                            .build())
                .toList())
        .startDate(b.startDate())
        .endDate(b.endDate())
        .examinationSiteType(b.site().type().name())
        .examinationSiteName(b.site().name())
        .examinationSiteAddress(b.site().address())
        .status(b.status().name())
        .createdBy(d.createdBy())
        .createdAt(d.createdAt())
        .updatedAt(d.updatedAt())
        .rowVersion(b.rowVersion())
        .services(
            b.services().stream()
                .map(
                    s ->
                        ServiceResponse.builder()
                            .id(s.id().value())
                            .serviceId(s.serviceId().value())
                            .serviceCode(serviceOf(catalog, s.serviceId().value(), true))
                            .serviceName(serviceOf(catalog, s.serviceId().value(), false))
                            .referencePriceSnapshot(s.referencePriceSnapshot().amount())
                            .negotiatedPrice(s.negotiatedPrice().amount())
                            .displayOrder(s.displayOrder())
                            .active(s.active())
                            .rowVersion(s.rowVersion())
                            .build())
                .toList())
        .build();
  }

  private static String serviceOf(
      Map<UUID, ServiceCatalogQuery.Service> catalog, UUID serviceId, boolean code) {
    var service = catalog.get(serviceId);
    if (service == null) return null;
    return code ? service.code() : service.name();
  }

  @Builder
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
    return AuditSnapshot.builder()
        .batchCode(batchCode)
        .batchName(batchName)
        .days(days)
        .siteType(examinationSiteType)
        .siteName(examinationSiteName)
        .siteAddress(examinationSiteAddress)
        .status(status)
        .services(services)
        .build();
  }
}
