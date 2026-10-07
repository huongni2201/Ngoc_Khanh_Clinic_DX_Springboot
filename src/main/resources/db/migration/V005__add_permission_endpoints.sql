-- ============================================================================
-- V005: endpoint of each permission (ADR-0015)
--
--   * http_method + endpoint name the one API endpoint a permission protects.
--     endpoint is the route template declared by the controller, including its
--     path variable names; requests are authorized by an exact match on it.
--   * Permissions without an endpoint yet keep both columns NULL.
--   * The V003 roster codes are replaced by the matrix codes of V004
--     (PARTICIPANT_VIEW, PARTICIPANT_TEMPLATE_DOWNLOAD, PARTICIPANT_IMPORT).
--   * Four permissions outside the SRS Permission Matrix 4.4 are added for
--     existing endpoints; the matrix must be extended to list them.
-- ============================================================================

ALTER TABLE public.permissions ADD COLUMN http_method varchar(10) NULL;

ALTER TABLE public.permissions ADD COLUMN endpoint varchar(300) NULL;

ALTER TABLE public.permissions
    ADD CONSTRAINT ck_permissions_endpoint_pair CHECK ((http_method IS NULL) = (endpoint IS NULL)),
    ADD CONSTRAINT ck_permissions_http_method CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    ADD CONSTRAINT ck_permissions_endpoint_path CHECK (endpoint LIKE '/api/v1/%'),
    ADD CONSTRAINT uq_permissions_http_method_endpoint UNIQUE (http_method, endpoint);

DELETE FROM public.role_permissions
WHERE permission_id IN (
    SELECT id FROM public.permissions
    WHERE code IN ('HEALTH_EXAMINATION_PARTICIPANT_READ', 'HEALTH_EXAMINATION_PARTICIPANT_IMPORT'));

DELETE FROM public.permissions
WHERE code IN ('HEALTH_EXAMINATION_PARTICIPANT_READ', 'HEALTH_EXAMINATION_PARTICIPANT_IMPORT');

INSERT INTO public.permissions (code, name, description)
VALUES
    ('ORGANIZATION_DELETE', 'Delete organization', 'Organization. Not in the SRS Permission Matrix 4.4 yet.'),
    ('HEALTH_EXAMINATION_BATCH_DETAIL_VIEW', 'View examination batch details', 'Health Examination Batch. Not in the SRS Permission Matrix 4.4 yet.'),
    ('HEALTH_EXAMINATION_BATCH_DELETE', 'Delete examination batch', 'Health Examination Batch. Not in the SRS Permission Matrix 4.4 yet.'),
    ('SERVICE_CATALOG_VIEW', 'View service catalog', 'Clinic Master Data. Not in the SRS Permission Matrix 4.4 yet.');

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM public.roles r
JOIN public.permissions p
  ON p.code IN ('ORGANIZATION_DELETE', 'HEALTH_EXAMINATION_BATCH_DETAIL_VIEW', 'HEALTH_EXAMINATION_BATCH_DELETE', 'SERVICE_CATALOG_VIEW')
WHERE r.code = 'CLINIC_MANAGER';

UPDATE public.permissions p
SET http_method = endpoint_map.http_method, endpoint = endpoint_map.endpoint
FROM (
    VALUES
        ('ORGANIZATION_SEARCH', 'GET', '/api/v1/organizations'),
        ('ORGANIZATION_CREATE', 'POST', '/api/v1/organizations'),
        ('ORGANIZATION_VIEW', 'GET', '/api/v1/organizations/{organizationId}'),
        ('ORGANIZATION_UPDATE', 'PUT', '/api/v1/organizations/{organizationId}'),
        ('ORGANIZATION_DELETE', 'DELETE', '/api/v1/organizations/{organizationId}'),
        ('HEALTH_EXAMINATION_BATCH_VIEW', 'GET', '/api/v1/organizations/{organizationId}/health-examination-batches'),
        ('HEALTH_EXAMINATION_BATCH_CREATE', 'POST', '/api/v1/organizations/{organizationId}/health-examination-batches'),
        ('HEALTH_EXAMINATION_BATCH_DETAIL_VIEW', 'GET', '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}'),
        ('HEALTH_EXAMINATION_BATCH_UPDATE', 'PUT', '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}'),
        ('HEALTH_EXAMINATION_BATCH_DELETE', 'DELETE', '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}'),
        ('PARTICIPANT_VIEW', 'GET', '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants'),
        ('PARTICIPANT_TEMPLATE_DOWNLOAD', 'GET', '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/import-template'),
        ('PARTICIPANT_IMPORT', 'POST', '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/imports'),
        ('SERVICE_CATALOG_VIEW', 'GET', '/api/v1/catalog/services')
) AS endpoint_map(code, http_method, endpoint)
WHERE p.code = endpoint_map.code;
