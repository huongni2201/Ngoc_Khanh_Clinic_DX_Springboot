-- Permission of the manual Participant changes of a health examination batch:
--   HEALTH_EXAMINATION_PARTICIPANT_MANAGE add one Participant, read one in full, edit and cancel it
-- Only the permission catalog is added. Roles that already exist receive the grants below; roles
-- created later must be granted explicitly. No permission is granted to every staff account.

INSERT INTO public.permissions (code, name, description)
VALUES
    ('HEALTH_EXAMINATION_PARTICIPANT_MANAGE',
     'Manage health examination Participants',
     'Add a Participant to a health examination batch by hand, read it in full, edit and cancel it.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
  ON permission.code = 'HEALTH_EXAMINATION_PARTICIPANT_MANAGE'
WHERE role.code IN ('ADMIN', 'CLINIC_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
