-- Local development only: this location is enabled exclusively by application-local.yaml.
INSERT INTO public.staff(id,staff_code,full_name,staff_type,is_active)
VALUES('01990000-0000-7000-8000-000000000002','LOCAL_BATCH_ACTOR','Local batch development actor','ADMIN',true)
ON CONFLICT(id) DO NOTHING;
INSERT INTO public.users(id,principal_type,staff_id,auth_provider,auth_subject,status,created_at)
VALUES('01990000-0000-7000-8000-000000000001','STAFF','01990000-0000-7000-8000-000000000002',
'LOCAL_MOCK','batch-development','ACTIVE',CURRENT_TIMESTAMP)
ON CONFLICT(id) DO NOTHING;
