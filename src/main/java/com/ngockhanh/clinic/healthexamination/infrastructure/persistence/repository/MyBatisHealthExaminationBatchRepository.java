package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchRecord;

@Repository
@RequiredArgsConstructor
public final class MyBatisHealthExaminationBatchRepository implements HealthExaminationBatchRepository {
    private final HealthExaminationBatchMyBatisMapper mapper;

    @Override
    public Optional<HealthExaminationBatchReference> findById(AggregateId id) {
        HealthExaminationBatchRecord record = mapper.findById(id.value());
        return Optional.ofNullable(record)
                .map(Converter::toDomain);
    }
    private static final class Converter {
        static HealthExaminationBatchReference toDomain(HealthExaminationBatchRecord record) {
            return new HealthExaminationBatchReference(
                    new AggregateId(record.id()), new AggregateId(record.organizationId()), record.startDate());
        }
    }
}
