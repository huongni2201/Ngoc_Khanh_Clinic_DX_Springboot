ALTER TABLE public.encounters
    ADD CONSTRAINT CK_encounters_status
    CHECK (status IN ('PREPARED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED'));

ALTER TABLE public.service_requests
    ADD CONSTRAINT CK_service_requests_status
    CHECK (status IN ('ORDERED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED'));

ALTER TABLE public.payment_authorizations
    ADD CONSTRAINT CK_payment_authorizations_status
    CHECK (status IN ('NOT_REQUIRED', 'PENDING', 'AUTHORIZED', 'WAIVED', 'REVOKED'));

ALTER TABLE public.payments
    ADD CONSTRAINT CK_payments_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'PARTIALLY_REFUNDED', 'REFUNDED'));

ALTER TABLE public.health_check_batches
    ADD CONSTRAINT CK_health_check_batches_status
    CHECK (status IN ('DRAFT', 'READY', 'IN_PROGRESS', 'RESULT_PROCESSING', 'FINALIZED', 'CLOSED', 'CANCELED'));

ALTER TABLE public.health_check_records
    ADD CONSTRAINT CK_health_check_records_status
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELED', 'REPLACED'));
