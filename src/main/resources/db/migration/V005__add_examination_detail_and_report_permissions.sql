-- Permissions of the examination detail matrix and the payment report of a health examination batch:
--   HEALTH_EXAMINATION_SERVICE_READ       view the examination detail matrix and export it to Excel
--   HEALTH_EXAMINATION_SERVICE_RECONCILE  import an examination detail Excel file
--   HEALTH_EXAMINATION_REPORT_READ        view the payment report and export it to Word
-- Only the permission catalog is added. Roles that already exist receive the grants below; roles
-- created later must be granted explicitly. No permission is granted to every staff account.

INSERT INTO public.permissions (code, name, description)
VALUES
    ('HEALTH_EXAMINATION_SERVICE_READ',
     'Read health examination service details',
     'View the Participant by service matrix of a health examination batch and export it to Excel.'),
    ('HEALTH_EXAMINATION_SERVICE_RECONCILE',
     'Reconcile health examination services',
     'Import an Excel file that records the services performed for Participants of a batch.'),
    ('HEALTH_EXAMINATION_REPORT_READ',
     'Read health examination payment report',
     'View the payment summary of a health examination batch and export it to Word.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
  ON permission.code IN (
      'HEALTH_EXAMINATION_SERVICE_READ',
      'HEALTH_EXAMINATION_SERVICE_RECONCILE',
      'HEALTH_EXAMINATION_REPORT_READ')
WHERE role.code IN ('ADMIN', 'CLINIC_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
