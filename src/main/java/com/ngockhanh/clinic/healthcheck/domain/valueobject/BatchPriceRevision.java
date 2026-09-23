package com.ngockhanh.clinic.healthcheck.domain.valueobject;

import java.util.UUID;

public record BatchPriceRevision(UUID batchId, UUID batchServiceId, Money oldPrice, Money newPrice, String reason) {
    public BatchPriceRevision {
        if (batchId == null || batchServiceId == null || oldPrice == null || newPrice == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Invalid batch price revision");
        }
    }
}
