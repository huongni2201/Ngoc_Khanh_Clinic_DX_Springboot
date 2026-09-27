package com.ngockhanh.clinic.healthcheck.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthcheck.application.port.BatchParticipantSummaryQuery;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantImportBatchQuery;
import com.ngockhanh.clinic.healthcheck.application.query.ParticipantSummary;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.PageResponse;

@Service
public final class ListBatchParticipantsUseCase {
    private final ParticipantImportBatchQuery batchQuery;
    private final BatchParticipantSummaryQuery participantQuery;

    public ListBatchParticipantsUseCase(ParticipantImportBatchQuery batchQuery,
                                       BatchParticipantSummaryQuery participantQuery) {
        this.batchQuery = batchQuery;
        this.participantQuery = participantQuery;
    }

    @Transactional(readOnly = true)
    public PageResponse<ParticipantSummary> execute(UUID organizationId, UUID batchId, int page, int size) {
        batchQuery.findById(batchId).filter(batch -> batch.organizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        long requestedOffset = (page - 1L) * size;
        if (requestedOffset > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid pagination");
        int offset = (int) requestedOffset;
        long total = participantQuery.countByBatch(batchId);
        int pages = (int) ((total + size - 1) / size);
        return new PageResponse<>(participantQuery.findByBatch(batchId, offset, size), page, size, total, pages);
    }
}
