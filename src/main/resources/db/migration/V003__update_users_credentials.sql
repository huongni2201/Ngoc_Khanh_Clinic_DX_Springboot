-- Existing accounts must be provisioned with a username and an encoded password.
-- Back up provider/subject mappings before applying to a populated shared database.
ALTER TABLE public.users
    DROP CONSTRAINT uq_users_auth_provider_auth_subject,
    DROP COLUMN auth_provider,
    DROP COLUMN auth_subject,
    ADD COLUMN username varchar(200) NULL,
    ADD COLUMN password text NULL,
    ADD CONSTRAINT uq_users_username UNIQUE (username);

COMMENT ON COLUMN public.users.password IS 'Encoded password hash only; never plaintext.';
