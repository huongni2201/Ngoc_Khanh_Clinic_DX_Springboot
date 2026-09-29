package com.ngockhanh.clinic.healthexamination.domain.valueobject;

public record BatchPriceRevision(
		AggregateId batchId,
		AggregateId batchServiceId,
		Money oldPrice,
		Money newPrice,
		String reason) {

	public BatchPriceRevision {
		if (batchId == null || batchServiceId == null || oldPrice == null || newPrice == null || reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("Invalid batch price revision");
		}
	}
}
