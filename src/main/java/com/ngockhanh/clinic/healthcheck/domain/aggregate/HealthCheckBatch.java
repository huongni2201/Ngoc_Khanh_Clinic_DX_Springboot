package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;

import com.ngockhanh.clinic.healthcheck.domain.exception.BatchConfigurationLocked;
import com.ngockhanh.clinic.healthcheck.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.BatchPriceRevision;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.ExaminationSite;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HealthCheckBatch {
    private final UUID id;
    private final UUID companyId;
    private final String code;
    private final ExaminationSite site;
    private final UUID masterTemplateVersionId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Map<UUID, HealthCheckBatchService> services = new HashMap<>();
    private BatchStatus status = BatchStatus.DRAFT;

    public HealthCheckBatch(UUID id, UUID companyId, String code, ExaminationSite site, UUID masterTemplateVersionId) {
        this(id, companyId, code, site, masterTemplateVersionId, null, null);
    }

    public HealthCheckBatch(UUID id, UUID companyId, String code, ExaminationSite site, UUID masterTemplateVersionId,
                            LocalDate startDate, LocalDate endDate) {
        if (id == null || companyId == null || code == null || code.isBlank() || site == null || masterTemplateVersionId == null
                || (startDate != null && endDate != null && startDate.isAfter(endDate))) {
            throw new IllegalArgumentException("Invalid health-check batch");
        }
        this.id = id;
        this.companyId = companyId;
        this.code = code;
        this.site = site;
        this.masterTemplateVersionId = masterTemplateVersionId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static HealthCheckBatch restore(UUID id, UUID companyId, String code, ExaminationSite site,
                                           UUID masterTemplateVersionId, LocalDate startDate, LocalDate endDate,
                                           BatchStatus status, List<HealthCheckBatchService> services) {
        if (status == null || services == null) throw new IllegalArgumentException("Incomplete persisted batch");
        HealthCheckBatch batch = new HealthCheckBatch(id, companyId, code, site, masterTemplateVersionId, startDate, endDate);
        for (HealthCheckBatchService service : services) batch.addService(service);
        if (status != BatchStatus.DRAFT && services.isEmpty()) throw new IllegalArgumentException("Persisted batch has no service scope");
        batch.status = status;
        return batch;
    }

    public void addService(HealthCheckBatchService service) {
        if (status != BatchStatus.DRAFT) throw new BatchConfigurationLocked();
        if (service == null || service.batchId() != id) throw new IllegalArgumentException("Service outside batch");
        if (services.containsKey(service.id()) || services.values().stream().anyMatch(existing -> existing.serviceId() == service.serviceId())) {
            throw new DomainRuleViolation("Duplicate batch service");
        }
        services.put(service.id(), service);
    }

    public void markReady() {
        if (status != BatchStatus.DRAFT) throw new DomainRuleViolation("Batch cannot become READY");
        if (services.isEmpty()) throw new DomainRuleViolation("Batch needs service scope");
        status = BatchStatus.READY;
    }

    public BatchPriceRevision repriceService(UUID serviceId, Money price, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Repricing requires reason");
        if (status == BatchStatus.FINALIZED || status == BatchStatus.CLOSED || status == BatchStatus.CANCELED) {
            throw new DomainRuleViolation("Batch must be reopened before repricing");
        }
        HealthCheckBatchService service = services.get(serviceId);
        if (service == null) throw new IllegalArgumentException("Unknown batch service");
        BatchPriceRevision revision = new BatchPriceRevision(id, serviceId, service.negotiatedPrice(), price, reason);
        service.reprice(price);
        return revision;
    }

    public void advanceTo(BatchStatus next) {
        Boolean allowed = switch (status) {
            case READY -> next == BatchStatus.IN_PROGRESS;
            case IN_PROGRESS -> next == BatchStatus.RESULT_PROCESSING;
            case RESULT_PROCESSING -> next == BatchStatus.FINALIZED;
            case FINALIZED -> next == BatchStatus.CLOSED;
            default -> false;
        };
        if (!allowed) throw new DomainRuleViolation("Invalid batch transition");
        status = next;
    }

    public void reopenForRepricing(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reopen requires reason");
        if (status != BatchStatus.FINALIZED && status != BatchStatus.CLOSED) {
            throw new DomainRuleViolation("Only finalized or closed batch may reopen for repricing");
        }
        status = BatchStatus.RESULT_PROCESSING;
    }

    public BatchStatus status() { return status; }
    public UUID id() { return id; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public HealthCheckBatchService service(UUID serviceId) { return services.get(serviceId); }
}

