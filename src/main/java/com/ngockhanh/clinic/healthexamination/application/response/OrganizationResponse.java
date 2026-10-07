package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrganizationResponse(
    UUID id,
    String name,
    String taxCode,
    String phone,
    String email,
    String address,
    String contactFullName,
    String contactPhone,
    String contactEmail,
    String status,
    long rowVersion) {
  public static OrganizationResponse from(Organization organization) {
    return OrganizationResponse.builder()
        .id(organization.id().value())
        .name(organization.name())
        .taxCode(organization.taxCode())
        .phone(organization.phone())
        .email(organization.email())
        .address(organization.address())
        .contactFullName(organization.contactFullName())
        .contactPhone(organization.contactPhone())
        .contactEmail(organization.contactEmail())
        .status(organization.status().name())
        .rowVersion(organization.rowVersion())
        .build();
  }
}
