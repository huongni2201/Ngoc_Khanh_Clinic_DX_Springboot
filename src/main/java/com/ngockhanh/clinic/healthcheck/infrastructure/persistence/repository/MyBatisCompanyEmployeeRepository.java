package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.CompanyEmployee;
import com.ngockhanh.clinic.healthcheck.domain.repository.CompanyEmployeeRepository;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.converter.CompanyEmployeePersistenceConverter;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.CompanyEmployeeMyBatisMapper;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.CompanyEmployeeRecord;

@Repository
public final class MyBatisCompanyEmployeeRepository implements CompanyEmployeeRepository {
    private final CompanyEmployeeMyBatisMapper mapper;
    private final CompanyEmployeePersistenceConverter converter = new CompanyEmployeePersistenceConverter();

    public MyBatisCompanyEmployeeRepository(CompanyEmployeeMyBatisMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CompanyEmployee> findById(UUID id) {
        return Optional.ofNullable(converter.toDomain(mapper.findById(id)));
    }

    @Override
    public Optional<CompanyEmployee> findByCompanyAndCode(UUID companyId, String employeeCode) {
        return Optional.ofNullable(converter.toDomain(mapper.findByCompanyAndCode(companyId, employeeCode)));
    }

    @Override
    public Optional<CompanyEmployee> findByCompanyAndIdentificationNumber(UUID companyId, IdentificationNumber identificationNumber) {
        return Optional.ofNullable(converter.toDomain(mapper.findByCompanyAndIdentificationNumber(companyId, identificationNumber.value())));
    }

    @Override
    public void save(CompanyEmployee employee) {
        CompanyEmployeeRecord record = converter.toRecord(employee);
        if (mapper.update(record) == 0 && mapper.insert(record) != 1) {
            throw new IllegalStateException("Company employee was not saved");
        }
    }
}