ALTER TABLE public.organizations
    DROP CONSTRAINT uq_organizations_organization_code,
    DROP COLUMN organization_code;
