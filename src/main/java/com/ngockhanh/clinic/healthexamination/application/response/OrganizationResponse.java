package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrganizationResponse(
    UUID id,
    String code,
    String name,
    String organizationType,
    String taxCode,
    String phone,
    String email,
    String address,
    String contactFullName,
    String contactPosition,
    String contactPhone,
    String contactEmail,
    String status,
    long rowVersion) {
  public static OrganizationResponse from(Organization organization) {
    return OrganizationResponse.builder()
        .id(organization.id().value())
        .code(organization.code())
        .name(organization.name())
        .organizationType(organization.organizationType())
        .taxCode(organization.taxCode())
        .phone(organization.phone())
        .email(organization.email())
        .address(organization.address())
        .contactFullName(organization.contactFullName())
        .contactPosition(organization.contactPosition())
        .contactPhone(organization.contactPhone())
        .contactEmail(organization.contactEmail())
        .status(organization.status())
        .rowVersion(organization.rowVersion())
        .build();
  }
}
