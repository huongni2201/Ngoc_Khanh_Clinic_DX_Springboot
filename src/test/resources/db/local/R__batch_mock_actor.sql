-- Test-only account for batch creator foreign-key checks; its password hash is deliberately invalid.
INSERT INTO public.staff_members (id, staff_code, full_name, status)
VALUES ('01990000-0000-7000-8000-000000000002', 'LOCAL_BATCH_ACTOR', 'Local batch development actor', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, account_type, username, password_hash, staff_member_id, status)
VALUES (
    '01990000-0000-7000-8000-000000000001',
    'STAFF',
    'local_batch_actor',
    'LOCAL_TEST_ACCOUNT_CANNOT_AUTHENTICATE',
    '01990000-0000-7000-8000-000000000002',
    'ACTIVE'
)
ON CONFLICT (id) DO NOTHING;
