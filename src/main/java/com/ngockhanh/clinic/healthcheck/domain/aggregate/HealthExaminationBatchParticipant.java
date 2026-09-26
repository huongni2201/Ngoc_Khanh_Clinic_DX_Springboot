package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthcheck.domain.exception.DuplicateParticipantServiceAssignment;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.BatchPriceRevision;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.HealthExaminationBatchParticipantId;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;

public final class HealthExaminationBatchParticipant {
    private final HealthExaminationBatchParticipantId id;
    private final UUID batchId;
    private final UUID healthExaminationParticipantId;
    private final AdministrativeSnapshot rosterSnapshot;
    private final Map<UUID, HealthExaminationBatchParticipantService> assignments = new HashMap<>();

    private HealthExaminationBatchParticipant(HealthExaminationBatchParticipantId id, UUID batchId, UUID healthExaminationParticipantId,
                                             AdministrativeSnapshot rosterSnapshot) {
        if (id == null || batchId == null || healthExaminationParticipantId == null || rosterSnapshot == null) {
            throw new IllegalArgumentException("Invalid health-examination batch participant");
        }
        this.id = id;
        this.batchId = batchId;
        this.healthExaminationParticipantId = healthExaminationParticipantId;
        this.rosterSnapshot = rosterSnapshot;
    }

    public static HealthExaminationBatchParticipant create(HealthExaminationBatchParticipantId id, UUID batchId,
                                                           UUID healthExaminationParticipantId,
                                                           AdministrativeSnapshot rosterSnapshot) {
        return new HealthExaminationBatchParticipant(id, batchId, healthExaminationParticipantId, rosterSnapshot);
    }

    public static HealthExaminationBatchParticipant restore(HealthExaminationBatchParticipantId id, UUID batchId,
                                                            UUID healthExaminationParticipantId,
                                                            AdministrativeSnapshot snapshot,
                                                            List<HealthExaminationBatchParticipantService> assignments) {
        if (assignments == null) throw new IllegalArgumentException("Invalid persisted batch participant");
        HealthExaminationBatchParticipant participant = new HealthExaminationBatchParticipant(
                id, batchId, healthExaminationParticipantId, snapshot);
        for (HealthExaminationBatchParticipantService assignment : assignments) {
            if (assignment == null || participant.assignments.putIfAbsent(assignment.batchServiceId(), assignment) != null) {
                throw new IllegalArgumentException("Invalid persisted assignment list");
            }
        }
        return participant;
    }

    public void assignService(UUID assignmentId, UUID batchServiceId, UUID serviceRequestId, Money negotiatedPrice) {
        if (assignmentId == null || batchServiceId == null || serviceRequestId == null || negotiatedPrice == null) {
            throw new IllegalArgumentException("Incomplete participant service assignment");
        }
        if (assignments.putIfAbsent(batchServiceId,
                HealthExaminationBatchParticipantService.create(assignmentId, batchServiceId, serviceRequestId,
                        negotiatedPrice)) != null) {
            throw new DuplicateParticipantServiceAssignment();
        }
    }

    public void markServiceBillable(UUID serviceRequestId) {
        if (serviceRequestId == null) throw new IllegalArgumentException("Missing Service Request");
        Map.Entry<UUID, HealthExaminationBatchParticipantService> entry = assignments.entrySet().stream()
                .filter(item -> Objects.equals(item.getValue().serviceRequestId(), serviceRequestId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Service Request"));
        assignments.put(entry.getKey(), entry.getValue().markedBillable());
    }

    public void applyPriceRevision(BatchPriceRevision revision) {
        if (revision == null || !Objects.equals(revision.batchId(), batchId)) {
            throw new IllegalArgumentException("Price revision for another batch");
        }
        HealthExaminationBatchParticipantService assignment = assignments.get(revision.batchServiceId());
        if (assignment != null) {
            if (assignment.unitPrice().amount().compareTo(revision.oldPrice().amount()) != 0) {
                throw new DomainRuleViolation("Stale batch price revision");
            }
            assignments.put(revision.batchServiceId(), assignment.withUnitPrice(revision.newPrice()));
        }
    }

    public HealthExaminationBatchParticipantService assignmentFor(UUID batchServiceId) {
        return assignments.get(batchServiceId);
    }

    public HealthExaminationBatchParticipantId id() { return id; }
    public UUID batchId() { return batchId; }
    public UUID healthExaminationParticipantId() { return healthExaminationParticipantId; }
    public AdministrativeSnapshot rosterSnapshot() { return rosterSnapshot; }
    public List<HealthExaminationBatchParticipantService> assignments() { return List.copyOf(assignments.values()); }
}
