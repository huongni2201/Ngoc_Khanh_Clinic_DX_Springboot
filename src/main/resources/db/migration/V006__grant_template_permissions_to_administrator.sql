-- ============================================================================
-- V006: document and notification templates belong to the Administrator
--
-- SRS Permission Matrix 4.4 update: "Manage document templates" and "Manage
-- notification templates" are Full for Administrator and No for Clinic Manager,
-- matching the Administrator actor of SRS 4.1. V004 is not changed.
-- Sessions keep the permissions captured at sign-in; affected users must sign in
-- again.
-- ============================================================================

DELETE FROM public.role_permissions
WHERE role_id = (SELECT id FROM public.roles WHERE code = 'CLINIC_MANAGER')
  AND permission_id IN (
      SELECT id FROM public.permissions
      WHERE code IN ('MASTER_DATA_DOCUMENT_TEMPLATE_MANAGE', 'MASTER_DATA_NOTIFICATION_TEMPLATE_MANAGE'));

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM public.roles r
JOIN public.permissions p
  ON p.code IN ('MASTER_DATA_DOCUMENT_TEMPLATE_MANAGE', 'MASTER_DATA_NOTIFICATION_TEMPLATE_MANAGE')
WHERE r.code = 'ADMINISTRATOR'
ON CONFLICT (role_id, permission_id) DO NOTHING;

UPDATE public.permissions
SET description = 'Clinic Master Data. UC-114 to UC-123.'
WHERE code IN ('MASTER_DATA_DOCUMENT_TEMPLATE_MANAGE', 'MASTER_DATA_NOTIFICATION_TEMPLATE_MANAGE');
