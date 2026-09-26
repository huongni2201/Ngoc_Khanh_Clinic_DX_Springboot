package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.converter;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.OrganizationId;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.OrganizationRecord;

public final class OrganizationPersistenceConverter {
    public Organization toDomain(OrganizationRecord record) {
        if (record == null) return null;
        return Organization.restore(new OrganizationId(record.id()), record.organizationCode(), record.organizationName(), record.taxCode(),
                record.address(), record.contactName(), record.contactPhone(), record.contactJobTitle(),
                record.note(), record.status());
    }

    public OrganizationRecord toRecord(Organization organization) {
        return new OrganizationRecord(organization.id().value(), organization.code(), organization.name(), organization.taxCode(),
                organization.address(), organization.contactName(), organization.contactPhone(),
                organization.contactJobTitle(), organization.note(), organization.status(), null, null, 0L);
    }
}
