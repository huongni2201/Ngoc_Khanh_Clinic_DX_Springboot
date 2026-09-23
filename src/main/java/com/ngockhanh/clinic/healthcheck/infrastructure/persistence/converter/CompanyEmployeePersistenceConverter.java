package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.converter;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.CompanyEmployee;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.CompanyEmployeeRecord;

public final class CompanyEmployeePersistenceConverter {
    public CompanyEmployee toDomain(CompanyEmployeeRecord record) {
        if (record == null) return null;
        return CompanyEmployee.restore(record.id(), record.companyId(), record.employeeCode(),
                IdentificationNumber.of(record.identificationNumber()), record.fullName(), record.dateOfBirth(),
                record.sex(), record.departmentName(), record.jobTitle(), record.occupation(), record.status(), record.patientId());
    }

    public CompanyEmployeeRecord toRecord(CompanyEmployee employee) {
        return new CompanyEmployeeRecord(employee.id(), employee.companyId(), employee.patientId(), employee.employeeCode(),
                employee.identificationNumber().value(), employee.fullName(), employee.dateOfBirth(), employee.sex(),
                employee.departmentName(), employee.jobTitle(), employee.occupation(), employee.status(), null, null);
    }
}