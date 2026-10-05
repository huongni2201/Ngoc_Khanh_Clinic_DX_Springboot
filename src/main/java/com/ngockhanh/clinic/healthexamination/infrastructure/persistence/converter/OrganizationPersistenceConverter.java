package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.converter;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.OrganizationRecord;

public final class OrganizationPersistenceConverter {
  public Organization toDomain(OrganizationRecord record) {
    if (record == null) return null;
    return Organization.restore(
        new AggregateId(record.id()),
        record.code(),
        record.name(),
        record.organizationType(),
        record.taxCode(),
        record.phone(),
        record.email(),
        record.address(),
        record.contactFullName(),
        record.contactPosition(),
        record.contactPhone(),
        record.contactEmail(),
        record.status(),
        record.rowVersion());
  }

  public OrganizationRecord toRecord(Organization organization) {
    return new OrganizationRecord(
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
        null,
        null,
        organization.rowVersion());
  }
}
