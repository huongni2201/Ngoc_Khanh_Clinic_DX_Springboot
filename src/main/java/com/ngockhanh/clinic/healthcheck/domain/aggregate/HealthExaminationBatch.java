package com.ngockhanh.clinic.healthcheck.domain.aggregate;
import com.ngockhanh.clinic.healthcheck.domain.entity.HealthExaminationBatchService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthcheck.domain.exception.BatchConfigurationLocked;
import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.BatchPriceRevision;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.ExaminationSite;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;

public final class HealthExaminationBatch {
    private final UUID id;
    private final UUID companyId;
    private final String code;
    private final ExaminationSite site;
    private final UUID masterTemplateVersionId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Map<UUID, HealthExaminationBatchService> services = new HashMap<>();
    private BatchStatus status = BatchStatus.DRAFT;
    private LocalDateTime finalizedAt;
    private LocalDateTime closedAt;

    private HealthExaminationBatch(UUID id, UUID companyId, String code, ExaminationSite site, UUID masterTemplateVersionId,
                             LocalDate startDate, LocalDate endDate) {
        if (id == null || companyId == null || code == null || code.isBlank() || site == null || masterTemplateVersionId == null
                || (startDate != null && endDate != null && startDate.isAfter(endDate))) {
            throw new IllegalArgumentException("Invalid health-examination batch");
        }
        this.id = id;
        this.companyId = companyId;
        this.code = code;
        this.site = site;
        this.masterTemplateVersionId = masterTemplateVersionId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static HealthExaminationBatch create(UUID id, UUID companyId, String code, ExaminationSite site,
                                          UUID masterTemplateVersionId) {
        return create(id, companyId, code, site, masterTemplateVersionId, null, null);
    }
    public static HealthExaminationBatch create(UUID id, UUID companyId, String code, ExaminationSite site,
                                          UUID masterTemplateVersionId, LocalDate startDate, LocalDate endDate) {
        return new HealthExaminationBatch(id, companyId, code, site, masterTemplateVersionId, startDate, endDate);
    }

    public static HealthExaminationBatch restore(UUID id, UUID companyId, String code, ExaminationSite site,
                                           UUID masterTemplateVersionId, LocalDate startDate, LocalDate endDate,
                                           BatchStatus status, List<HealthExaminationBatchService> services) {
        return restore(id, companyId, code, site, masterTemplateVersionId, startDate, endDate,
                status, services, null, null);
    }

    public static HealthExaminationBatch restore(UUID id, UUID companyId, String code, ExaminationSite site,
                                           UUID masterTemplateVersionId, LocalDate startDate, LocalDate endDate,
                                           BatchStatus status, List<HealthExaminationBatchService> services,
                                           LocalDateTime finalizedAt, LocalDateTime closedAt) {
        if (status == null || services == null) throw new IllegalArgumentException("Incomplete persisted batch");
        if ((status == BatchStatus.FINALIZED || status == BatchStatus.CLOSED) != (finalizedAt != null)
                || (status == BatchStatus.CLOSED) != (closedAt != null)) {
            throw new IllegalArgumentException("Persisted batch lifecycle timestamps do not match status");
        }
        HealthExaminationBatch batch = new HealthExaminationBatch(id, companyId, code, site, masterTemplateVersionId, startDate, endDate);
        for (HealthExaminationBatchService service : services) batch.attachService(service);
        if (status != BatchStatus.DRAFT && status != BatchStatus.CANCELED && services.isEmpty()) {
            throw new IllegalArgumentException("Persisted batch has no service scope");
        }
        batch.status = status;
        batch.finalizedAt = finalizedAt;
        batch.closedAt = closedAt;
        return batch;
    }

    public void addService(HealthExaminationBatchService service) {
        if (status != BatchStatus.DRAFT) throw new BatchConfigurationLocked();
        attachService(service);
    }

    private void attachService(HealthExaminationBatchService service) {
        if (service == null || !Objects.equals(service.batchId(), id)) {
            throw new IllegalArgumentException("Service outside batch");
        }
        if (services.containsKey(service.id())
                || services.values().stream().anyMatch(existing -> Objects.equals(existing.serviceId(), service.serviceId()))) {
            throw new DomainRuleViolation("Duplicate batch service");
        }
        services.put(service.id(), service);
    }

    public void markReady() {
        if (status != BatchStatus.DRAFT) throw new DomainRuleViolation("Batch cannot become READY");
        if (services.isEmpty()) throw new DomainRuleViolation("Batch needs service scope");
        status = BatchStatus.READY;
    }

    public BatchPriceRevision repriceService(UUID batchServiceId, Money price, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Repricing requires reason");
        if (status == BatchStatus.FINALIZED || status == BatchStatus.CLOSED || status == BatchStatus.CANCELED) {
            throw new DomainRuleViolation("Batch must be reopened before repricing");
        }
        HealthExaminationBatchService service = services.get(batchServiceId);
        if (service == null) throw new IllegalArgumentException("Unknown batch service");
        BatchPriceRevision revision = new BatchPriceRevision(id, batchServiceId, service.negotiatedPrice(), price, reason);
        services.put(batchServiceId, service.withNegotiatedPrice(price));
        return revision;
    }

    public void start() {
        transition(BatchStatus.READY, BatchStatus.IN_PROGRESS);
    }

    public void startResultProcessing() {
        transition(BatchStatus.IN_PROGRESS, BatchStatus.RESULT_PROCESSING);
    }

    public void finalizeBatch(LocalDateTime finalizedAt) {
        if (finalizedAt == null) throw new IllegalArgumentException("Missing finalization timestamp");
        transition(BatchStatus.RESULT_PROCESSING, BatchStatus.FINALIZED);
        this.finalizedAt = finalizedAt;
    }

    public void close(LocalDateTime closedAt) {
        if (closedAt == null) throw new IllegalArgumentException("Missing close timestamp");
        transition(BatchStatus.FINALIZED, BatchStatus.CLOSED);
        this.closedAt = closedAt;
    }

    public void cancel(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Cancellation requires reason");
        if (status != BatchStatus.DRAFT && status != BatchStatus.READY
                && status != BatchStatus.IN_PROGRESS && status != BatchStatus.RESULT_PROCESSING) {
            throw new DomainRuleViolation("Batch cannot be canceled from its current state");
        }
        status = BatchStatus.CANCELED;
    }

    private void transition(BatchStatus expected, BatchStatus next) {
        if (status != expected) throw new DomainRuleViolation("Invalid batch transition");
        status = next;
    }

    public void reopenForRepricing(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reopen requires reason");
        if (status != BatchStatus.FINALIZED && status != BatchStatus.CLOSED) {
            throw new DomainRuleViolation("Only finalized or closed batch may reopen for repricing");
        }
        status = BatchStatus.RESULT_PROCESSING;
        finalizedAt = null;
        closedAt = null;
    }

    public UUID id() { return id; }
    public UUID companyId() { return companyId; }
    public String code() { return code; }
    public ExaminationSite site() { return site; }
    public UUID masterTemplateVersionId() { return masterTemplateVersionId; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public BatchStatus status() { return status; }
    public LocalDateTime finalizedAt() { return finalizedAt; }
    public LocalDateTime closedAt() { return closedAt; }
    public List<HealthExaminationBatchService> services() { return List.copyOf(services.values()); }
    public HealthExaminationBatchService service(UUID batchServiceId) { return services.get(batchServiceId); }
}
