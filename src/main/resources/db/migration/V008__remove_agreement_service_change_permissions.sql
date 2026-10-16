-- The services of an agreement are edited only while it is a DRAFT (PUT /agreements/{agreementId}).
-- Adding or retiring a service of an ACTIVE agreement is gone, so its two permissions go with it.
DELETE FROM public.role_permissions
WHERE permission_id IN (
    SELECT id FROM public.permissions
    WHERE code IN (
        'HEALTH_EXAMINATION_AGREEMENT_SERVICE_CONFIGURE',
        'HEALTH_EXAMINATION_AGREEMENT_SERVICE_DEACTIVATE'));

DELETE FROM public.permissions
WHERE code IN (
    'HEALTH_EXAMINATION_AGREEMENT_SERVICE_CONFIGURE',
    'HEALTH_EXAMINATION_AGREEMENT_SERVICE_DEACTIVATE');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.permissions
        WHERE code LIKE 'HEALTH\_EXAMINATION\_AGREEMENT\_SERVICE\_%'
           OR endpoint LIKE '%/agreements/{agreementId}/services%') THEN
        RAISE EXCEPTION 'A permission still names the removed agreement service routes';
    END IF;
END
$$;
