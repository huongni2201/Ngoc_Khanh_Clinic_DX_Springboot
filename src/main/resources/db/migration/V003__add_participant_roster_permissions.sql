-- Permissions of the Participant roster endpoints of a health examination batch:
--   HEALTH_EXAMINATION_PARTICIPANT_READ   list the roster of a batch
--   HEALTH_EXAMINATION_PARTICIPANT_IMPORT download the Excel template and import Participants
-- Only the permission catalog is added. Roles that already exist receive the grants below; roles
-- created later must be granted explicitly. No permission is granted to every staff account.

INSERT INTO public.permissions (code, name, description)
VALUES
    ('HEALTH_EXAMINATION_PARTICIPANT_READ',
     'Read health examination Participants',
     'List the Participant roster of a health examination batch.'),
    ('HEALTH_EXAMINATION_PARTICIPANT_IMPORT',
     'Import health examination Participants',
     'Download the Participant Excel template and add Participants to a health examination batch.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
  ON permission.code IN ('HEALTH_EXAMINATION_PARTICIPANT_READ', 'HEALTH_EXAMINATION_PARTICIPANT_IMPORT')
WHERE role.code IN ('ADMIN', 'CLINIC_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
