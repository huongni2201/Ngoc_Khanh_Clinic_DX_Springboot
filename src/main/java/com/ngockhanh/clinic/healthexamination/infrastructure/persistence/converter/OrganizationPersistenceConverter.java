package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.converter;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.OrganizationRecord;

public final class OrganizationPersistenceConverter {
  public Organization toDomain(OrganizationRecord record) {
    if (record == null) return null;
    return Organization.restore(
        new AggregateId(record.id()),
        record.name(),
        record.taxCode(),
        record.phone(),
        record.email(),
        record.address(),
        record.contactFullName(),
        record.contactPhone(),
        record.contactEmail(),
        record.status(),
        record.rowVersion());
  }

  public OrganizationRecord toRecord(Organization organization) {
    return OrganizationRecord.builder()
        .id(organization.id().value())
        .name(organization.name())
        .taxCode(organization.taxCode())
        .phone(organization.phone())
        .email(organization.email())
        .address(organization.address())
        .contactFullName(organization.contactFullName())
        .contactPhone(organization.contactPhone())
        .contactEmail(organization.contactEmail())
        .status(organization.status())
        .createdAt(null)
        .updatedAt(null)
        .rowVersion(organization.rowVersion())
        .build();
  }
}
