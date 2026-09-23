package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.converter;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.Company;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.CompanyRecord;

public final class CompanyPersistenceConverter {
    public Company toDomain(CompanyRecord record) {
        if (record == null) return null;
        return Company.restore(record.id(), record.companyCode(), record.companyName(), record.taxCode(),
                record.address(), record.contactName(), record.contactPhone(), record.contactJobTitle(),
                record.note(), record.status());
    }

    public CompanyRecord toRecord(Company company) {
        return new CompanyRecord(company.id(), company.code(), company.name(), company.taxCode(), company.address(),
                company.contactName(), company.contactPhone(), company.contactJobTitle(), company.note(),
                company.status(), null, null, null);
    }
}