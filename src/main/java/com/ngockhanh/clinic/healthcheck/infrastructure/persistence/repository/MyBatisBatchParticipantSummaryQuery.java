package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.application.port.BatchParticipantSummaryQuery;
import com.ngockhanh.clinic.healthcheck.application.query.ParticipantSummary;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.BatchParticipantSummaryMyBatisMapper;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.BatchParticipantSummaryRecord;

@Repository
public final class MyBatisBatchParticipantSummaryQuery implements BatchParticipantSummaryQuery {
    private final BatchParticipantSummaryMyBatisMapper mapper;
    private final JsonMapper jsonMapper;

    public MyBatisBatchParticipantSummaryQuery(BatchParticipantSummaryMyBatisMapper mapper, JsonMapper jsonMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<ParticipantSummary> findByBatch(UUID batchId, int offset, int limit) {
        return mapper.findByBatch(batchId, offset, limit).stream().map(this::toSummary).toList();
    }

    @Override
    public long countByBatch(UUID batchId) {
        return mapper.countByBatch(batchId);
    }

    private ParticipantSummary toSummary(BatchParticipantSummaryRecord record) {
        try {
            AdministrativeSnapshot snapshot = jsonMapper.readValue(record.administrativeSnapshotJson(),
                    AdministrativeSnapshot.class);
            return new ParticipantSummary(record.batchParticipantId(), record.participantId(), record.participantCode(),
                    record.departmentName(), record.jobTitle(), record.occupation(), snapshot, record.status(), record.createdAt());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Unable to read stored participant snapshot", failure);
        }
    }
}
