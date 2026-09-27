package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportBatchContext;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantImportBatchQuery;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.ParticipantImportBatchQueryMapper;

@Repository
public final class MyBatisParticipantImportBatchQuery implements ParticipantImportBatchQuery {
    private final ParticipantImportBatchQueryMapper mapper;

    public MyBatisParticipantImportBatchQuery(ParticipantImportBatchQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ParticipantImportBatchContext> findById(UUID batchId) {
        return Optional.ofNullable(mapper.findById(batchId));
    }
}
