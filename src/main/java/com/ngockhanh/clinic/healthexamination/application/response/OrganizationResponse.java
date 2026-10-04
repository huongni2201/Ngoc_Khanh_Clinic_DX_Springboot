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
    return new OrganizationResponse(
        organization.id().value(),
        organization.code(),
        organization.name(),
        organization.organizationType(),
        organization.taxCode(),
        organization.phone(),
        organization.email(),
        organization.address(),
        organization.contactFullName(),
        organization.contactPosition(),
        organization.contactPhone(),
        organization.contactEmail(),
        organization.status(),
        organization.rowVersion());
  }
}
