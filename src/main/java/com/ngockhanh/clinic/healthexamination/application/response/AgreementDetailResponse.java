package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository.AgreementDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/**
 * One agreement with its services. It holds the validity period and the negotiated prices; the
 * examination days and sites belong to sessions.
 *
 * @param validFrom first day the agreement applies, or null
 * @param validTo last day the agreement applies, or null
 * @param services every service line in display order; a retired line has {@code active = false}
 */
@Builder
public record AgreementDetailResponse(
    UUID id,
    UUID organizationId,
    String agreementCode,
    String agreementName,
    LocalDate validFrom,
    LocalDate validTo,
    String status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion,
    List<ServiceResponse> services) {
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
   * Maps stored agreement details to the response.
   *
   * @param catalog catalog services keyed by id; a service missing from it gets a {@code null} code
   *     and name
   */
  public static AgreementDetailResponse from(
      AgreementDetails d, Map<UUID, ServiceCatalogQuery.Service> catalog) {
    var a = d.agreement();
    return AgreementDetailResponse.builder()
        .id(a.id().value())
        .organizationId(a.organizationId().value())
        .agreementCode(a.code())
        .agreementName(a.name())
        .validFrom(a.validFrom())
        .validTo(a.validTo())
        .status(a.status().name())
        .createdBy(d.createdBy())
        .createdAt(d.createdAt())
        .updatedAt(d.updatedAt())
        .rowVersion(a.rowVersion())
        .services(
            a.services().stream()
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
      String agreementCode,
      String agreementName,
      LocalDate validFrom,
      LocalDate validTo,
      String status,
      List<ServiceResponse> services) {}

  public AuditSnapshot auditSummary() {
    return AuditSnapshot.builder()
        .agreementCode(agreementCode)
        .agreementName(agreementName)
        .validFrom(validFrom)
        .validTo(validTo)
        .status(status)
        .services(services)
        .build();
  }
}
