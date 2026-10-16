-- Catalog management for a fresh development database (owner request: 2026-10-16).
-- V001-V006 remain unchanged. Flyway applies this file once, in one transaction.

-- Versions and timestamps used by catalog management.
ALTER TABLE public.departments
    ADD COLUMN row_version bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_departments_catalog_version CHECK (row_version >= 0);

ALTER TABLE public.rooms
    ADD COLUMN row_version bigint NOT NULL DEFAULT 0,
    ADD COLUMN created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT ck_rooms_catalog_version CHECK (row_version >= 0);

ALTER TABLE public.specialties
    ADD COLUMN row_version bigint NOT NULL DEFAULT 0,
    ADD COLUMN created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT ck_specialties_catalog_version CHECK (row_version >= 0);

ALTER TABLE public.medicines
    ADD COLUMN row_version bigint NOT NULL DEFAULT 0,
    ADD COLUMN created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT ck_medicines_catalog_version CHECK (row_version >= 0);

ALTER TABLE public.services
    ADD CONSTRAINT ck_services_catalog_version CHECK (row_version >= 0);

-- Prevent code duplicates that differ only by case or surrounding spaces.

CREATE UNIQUE INDEX uq_departments_code_canonical
    ON public.departments (upper(btrim(code)));

CREATE UNIQUE INDEX uq_rooms_code_canonical
    ON public.rooms (upper(btrim(code)));

CREATE UNIQUE INDEX uq_specialties_code_canonical
    ON public.specialties (upper(btrim(code)));

CREATE UNIQUE INDEX uq_services_code_canonical
    ON public.services (upper(btrim(code)));

CREATE UNIQUE INDEX uq_medicines_code_canonical
    ON public.medicines (upper(btrim(code)));

-- Parent dependency checks and list filters.
CREATE INDEX idx_rooms_department_active
    ON public.rooms (department_id, active);
CREATE INDEX idx_services_performing_dept_active
    ON public.services (performing_department_id, active);
CREATE INDEX idx_services_specialty_active
    ON public.services (specialty_id, active)
    WHERE specialty_id IS NOT NULL;

-- Declare each permission and its endpoint together (ADR-0015).
INSERT INTO public.permissions (code, name, description, http_method, endpoint, module, use_cases)
SELECT seed.*, 'Clinic Master Data', CASE
    WHEN seed.code LIKE 'CATALOG_DEPARTMENT_%' THEN 'UC-110'
    WHEN seed.code LIKE 'CATALOG_SPECIALTY_%' THEN 'UC-111'
    WHEN seed.code LIKE 'CATALOG_ROOM_%' THEN 'UC-112'
    WHEN seed.code LIKE 'CATALOG_SERVICE_%' THEN 'UC-113, UC-114'
    WHEN seed.code LIKE 'CATALOG_MEDICINE_%' THEN 'UC-115'
END
FROM (VALUES

    ('CATALOG_DEPARTMENT_VIEW', 'List departments', 'Catalog management: list departments with pagination, search and filters.', 'GET', '/api/v1/catalog/management/departments'),
    ('CATALOG_DEPARTMENT_DETAIL_VIEW', 'View department detail', 'Catalog management: get one department by ID.', 'GET', '/api/v1/catalog/management/departments/{id}'),
    ('CATALOG_DEPARTMENT_CREATE', 'Create department', 'Catalog management: create a new department.', 'POST', '/api/v1/catalog/management/departments'),
    ('CATALOG_DEPARTMENT_UPDATE', 'Update department', 'Catalog management: update an existing department.', 'PUT', '/api/v1/catalog/management/departments/{id}'),
    ('CATALOG_DEPARTMENT_DEACTIVATE', 'Deactivate department', 'Catalog management: deactivate a department (soft-delete via active=false).', 'DELETE', '/api/v1/catalog/management/departments/{id}'),
    ('CATALOG_DEPARTMENT_REACTIVATE', 'Reactivate department', 'Catalog management: reactivate an inactive department.', 'POST', '/api/v1/catalog/management/departments/{id}/reactivate'),
    ('CATALOG_ROOM_VIEW', 'List rooms', 'Catalog management: list rooms with pagination, search and filters.', 'GET', '/api/v1/catalog/management/rooms'),
    ('CATALOG_ROOM_DETAIL_VIEW', 'View room detail', 'Catalog management: get one room by ID.', 'GET', '/api/v1/catalog/management/rooms/{id}'),
    ('CATALOG_ROOM_CREATE', 'Create room', 'Catalog management: create a new room.', 'POST', '/api/v1/catalog/management/rooms'),
    ('CATALOG_ROOM_UPDATE', 'Update room', 'Catalog management: update an existing room.', 'PUT', '/api/v1/catalog/management/rooms/{id}'),
    ('CATALOG_ROOM_DEACTIVATE', 'Deactivate room', 'Catalog management: deactivate a room (active=false).', 'DELETE', '/api/v1/catalog/management/rooms/{id}'),
    ('CATALOG_ROOM_REACTIVATE', 'Reactivate room', 'Catalog management: reactivate an inactive room.', 'POST', '/api/v1/catalog/management/rooms/{id}/reactivate'),
    ('CATALOG_SPECIALTY_VIEW', 'List specialties', 'Catalog management: list specialties with pagination, search.', 'GET', '/api/v1/catalog/management/specialties'),
    ('CATALOG_SPECIALTY_DETAIL_VIEW', 'View specialty detail', 'Catalog management: get one specialty by ID.', 'GET', '/api/v1/catalog/management/specialties/{id}'),
    ('CATALOG_SPECIALTY_CREATE', 'Create specialty', 'Catalog management: create a new specialty.', 'POST', '/api/v1/catalog/management/specialties'),
    ('CATALOG_SPECIALTY_UPDATE', 'Update specialty', 'Catalog management: update an existing specialty.', 'PUT', '/api/v1/catalog/management/specialties/{id}'),
    ('CATALOG_SPECIALTY_DEACTIVATE', 'Deactivate specialty', 'Catalog management: deactivate a specialty (active=false).', 'DELETE', '/api/v1/catalog/management/specialties/{id}'),
    ('CATALOG_SPECIALTY_REACTIVATE', 'Reactivate specialty', 'Catalog management: reactivate an inactive specialty.', 'POST', '/api/v1/catalog/management/specialties/{id}/reactivate'),
    ('CATALOG_SERVICE_VIEW', 'List managed services', 'Catalog management: list services (all statuses) with pagination, search and filters.', 'GET', '/api/v1/catalog/management/services'),
    ('CATALOG_SERVICE_DETAIL_VIEW', 'View managed service detail', 'Catalog management: get one service by ID (management view, any status).', 'GET', '/api/v1/catalog/management/services/{id}'),
    ('CATALOG_SERVICE_CREATE', 'Create service', 'Catalog management: create a new service.', 'POST', '/api/v1/catalog/management/services'),
    ('CATALOG_SERVICE_UPDATE', 'Update service', 'Catalog management: update an existing service.', 'PUT', '/api/v1/catalog/management/services/{id}'),
    ('CATALOG_SERVICE_DEACTIVATE', 'Deactivate service', 'Catalog management: deactivate a service (active=false).', 'DELETE', '/api/v1/catalog/management/services/{id}'),
    ('CATALOG_SERVICE_REACTIVATE', 'Reactivate service', 'Catalog management: reactivate an inactive service.', 'POST', '/api/v1/catalog/management/services/{id}/reactivate'),
    ('CATALOG_MEDICINE_VIEW', 'List medicines', 'Catalog management: list medicines with pagination, search.', 'GET', '/api/v1/catalog/management/medicines'),
    ('CATALOG_MEDICINE_DETAIL_VIEW', 'View medicine detail', 'Catalog management: get one medicine by ID.', 'GET', '/api/v1/catalog/management/medicines/{id}'),
    ('CATALOG_MEDICINE_CREATE', 'Create medicine', 'Catalog management: create a new medicine.', 'POST', '/api/v1/catalog/management/medicines'),
    ('CATALOG_MEDICINE_UPDATE', 'Update medicine', 'Catalog management: update an existing medicine.', 'PUT', '/api/v1/catalog/management/medicines/{id}'),
    ('CATALOG_MEDICINE_DEACTIVATE', 'Deactivate medicine', 'Catalog management: deactivate a medicine (active=false).', 'DELETE', '/api/v1/catalog/management/medicines/{id}'),
    ('CATALOG_MEDICINE_REACTIVATE', 'Reactivate medicine', 'Catalog management: reactivate an inactive medicine.', 'POST', '/api/v1/catalog/management/medicines/{id}/reactivate')
) AS seed(code, name, description, http_method, endpoint);

-- UC-110 through UC-115 belong to Clinic Manager.
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
CROSS JOIN public.permissions AS permission
WHERE role.code = 'CLINIC_MANAGER'
  AND permission.code LIKE 'CATALOG\_%' ESCAPE '\';
