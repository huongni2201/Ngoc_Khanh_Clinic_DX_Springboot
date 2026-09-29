package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.converter;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.OrganizationRecord;

public final class OrganizationPersistenceConverter {
    public Organization toDomain(OrganizationRecord record) {
        if (record == null) return null;
        return Organization.restore(new AggregateId(record.id()), record.organizationName(), record.taxCode(),
                record.address(), record.contactName(), record.contactPhone(), record.contactJobTitle(),
                record.note(), record.status(), record.rowVersion());
    }

    public OrganizationRecord toRecord(Organization organization) {
        return new OrganizationRecord(organization.id().value(), organization.name(), organization.taxCode(),
                organization.address(), organization.contactName(), organization.contactPhone(),
                organization.contactJobTitle(), organization.note(), organization.status(), null, null,
                organization.rowVersion());
    }
}
