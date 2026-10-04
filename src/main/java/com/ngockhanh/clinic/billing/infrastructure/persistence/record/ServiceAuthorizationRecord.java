package com.ngockhanh.clinic.billing.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.service_authorizations}. */
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
