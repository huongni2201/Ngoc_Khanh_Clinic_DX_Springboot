package com.ngockhanh.clinic.catalog.application.response;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery.ServiceItem;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ServiceCatalogItemResponse(
    UUID id, String code, String name, String serviceType, BigDecimal unitPrice, boolean active) {
  public static ServiceCatalogItemResponse from(ServiceItem item) {
    return ServiceCatalogItemResponse.builder()
        .id(item.id())
        .code(item.code())
        .name(item.name())
        .serviceType(item.serviceType())
        .unitPrice(item.unitPrice())
        .active(item.active())
        .build();
  }
}
