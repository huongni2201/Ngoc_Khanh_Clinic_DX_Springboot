package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.service_authorizations}. */
@Builder
public record ServiceAuthorizationRecord(
    UUID id,
    UUID serviceRequestId,
    String status,
    String authorizationSource,
    UUID sourceInvoiceId,
    UUID sourcePaymentId,
    String policyReference,
    long rowVersion,
    Instant authorizedAt,
    Instant revokedAt) {}
