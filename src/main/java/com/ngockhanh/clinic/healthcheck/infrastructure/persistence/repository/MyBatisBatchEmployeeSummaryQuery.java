package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.api.response.EmployeeListResponse;
import com.ngockhanh.clinic.healthcheck.application.port.BatchEmployeeSummaryQuery;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.BatchEmployeeSummaryMyBatisMapper;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.BatchEmployeeSummaryRecord;

@Repository
public final class MyBatisBatchEmployeeSummaryQuery implements BatchEmployeeSummaryQuery {
    private final BatchEmployeeSummaryMyBatisMapper mapper;

    public MyBatisBatchEmployeeSummaryQuery(BatchEmployeeSummaryMyBatisMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<EmployeeListResponse> findByBatch(UUID batchId, int offset, int limit,
                                                  String searchPattern, String sortType, String sortBy) {
        return mapper.findByBatch(batchId, offset, limit, searchPattern, sortType, sortBy)
                .stream().map(this::toEmployeeSummary).toList();
    }

    @Override
    public long countByBatch(UUID batchId, String searchPattern) {
        return mapper.countByBatch(batchId, searchPattern);
    }

    private EmployeeListResponse toEmployeeSummary(BatchEmployeeSummaryRecord record) {
        try {
            AdministrativeSnapshot snapshot = new AdministrativeSnapshot(record.fullNameSnapshot(),
                    record.dateOfBirthSnapshot(), record.sexSnapshot(),
                    IdentificationNumber.of(record.identificationNumberSnapshot()),
                    record.identificationNumberIssueDateSnapshot(), record.identificationNumberIssuePlaceSnapshot(),
                    record.ethnicitySnapshot(), record.subjectTypeSnapshot(), record.payerSourceSnapshot(),
                    record.bloodGroupSnapshot(), record.phoneSnapshot(), record.provinceSnapshot(), record.wardSnapshot(),
                    record.addressDetailSnapshot(), record.administrativeOccupationSnapshot(),
                    record.workplaceOrSchoolSnapshot(), record.healthExaminationReasonSnapshot());
            return new EmployeeListResponse(record.batchEmployeeId(), record.employeeId(), record.employeeCode(),
                    record.departmentName(), record.jobTitle(), record.occupation(), snapshot, record.status(), record.createdAt());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Unable to map stored employee snapshot", failure);
        }
    }
}
