package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.entity.HealthCheckBatchEmployeeService;
import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthcheck.domain.exception.DuplicateEmployeeServiceAssignment;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.BatchPriceRevision;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;

public final class HealthCheckBatchEmployee {
    private final UUID id;
    private final UUID batchId;
    private final AdministrativeSnapshot rosterSnapshot;
    private final Map<UUID, HealthCheckBatchEmployeeService> assignments = new HashMap<>();

    private HealthCheckBatchEmployee(UUID id, UUID batchId, AdministrativeSnapshot rosterSnapshot) {
        if (id == null || batchId == null || rosterSnapshot == null) {
            throw new IllegalArgumentException("Invalid batch employee");
        }
        this.id = id;
        this.batchId = batchId;
        this.rosterSnapshot = rosterSnapshot;
    }

    public static HealthCheckBatchEmployee create(UUID id, UUID batchId, AdministrativeSnapshot rosterSnapshot) {
        return new HealthCheckBatchEmployee(id, batchId, rosterSnapshot);
    }

    public static HealthCheckBatchEmployee restore(UUID id, UUID batchId, AdministrativeSnapshot snapshot,
                                                   List<HealthCheckBatchEmployeeService> assignments) {
        if (assignments == null) throw new IllegalArgumentException("Invalid persisted batch employee");
        HealthCheckBatchEmployee employee = new HealthCheckBatchEmployee(id, batchId, snapshot);
        for (HealthCheckBatchEmployeeService assignment : assignments) {
            if (assignment == null || employee.assignments.putIfAbsent(assignment.batchServiceId(), assignment) != null) {
                throw new IllegalArgumentException("Invalid persisted assignment list");
            }
        }
        return employee;
    }

    public void assignService(UUID assignmentId, UUID batchServiceId, UUID serviceRequestId, Money negotiatedPrice) {
        if (assignmentId == null || batchServiceId == null || serviceRequestId == null || negotiatedPrice == null) {
            throw new IllegalArgumentException("Incomplete employee service assignment");
        }
        if (assignments.putIfAbsent(batchServiceId,
                HealthCheckBatchEmployeeService.create(assignmentId, batchServiceId, serviceRequestId, negotiatedPrice)) != null) {
            throw new DuplicateEmployeeServiceAssignment();
        }
    }

    public void markServiceBillable(UUID serviceRequestId) {
        if (serviceRequestId == null) throw new IllegalArgumentException("Missing Service Request");
        Map.Entry<UUID, HealthCheckBatchEmployeeService> entry = assignments.entrySet().stream()
                .filter(item -> Objects.equals(item.getValue().serviceRequestId(), serviceRequestId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Service Request"));
        assignments.put(entry.getKey(), entry.getValue().markedBillable());
    }

    public void applyPriceRevision(BatchPriceRevision revision) {
        if (revision == null || !Objects.equals(revision.batchId(), batchId)) {
            throw new IllegalArgumentException("Price revision for another batch");
        }
        HealthCheckBatchEmployeeService assignment = assignments.get(revision.batchServiceId());
        if (assignment != null) {
            if (assignment.unitPrice().amount().compareTo(revision.oldPrice().amount()) != 0) {
                throw new DomainRuleViolation("Stale batch price revision");
            }
            assignments.put(revision.batchServiceId(), assignment.withUnitPrice(revision.newPrice()));
        }
    }

    public HealthCheckBatchEmployeeService assignmentFor(UUID batchServiceId) {
        return assignments.get(batchServiceId);
    }

    public UUID id() { return id; }
    public UUID batchId() { return batchId; }
    public AdministrativeSnapshot rosterSnapshot() { return rosterSnapshot; }
    public List<HealthCheckBatchEmployeeService> assignments() { return List.copyOf(assignments.values()); }
}