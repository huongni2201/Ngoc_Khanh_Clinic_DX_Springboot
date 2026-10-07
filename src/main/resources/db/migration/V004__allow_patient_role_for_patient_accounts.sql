-- Align trg_validate_account_role() with the access-control design (ADR-0015) and
-- AccountRoleAssignmentIntegrationTest:
--   * the PATIENT role may only be held by PATIENT accounts;
--   * every other role may only be held by STAFF accounts;
--   * the grantor must always be a STAFF account (a patient can never grant roles, not even to itself).
-- The V001 version rejected every non-STAFF target, so no patient account could receive the PATIENT role.

CREATE OR REPLACE FUNCTION public.trg_validate_account_role()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_target_type text;
    v_grantor_type text;
    v_role_code text;
BEGIN
    SELECT account_type INTO v_target_type
    FROM public.accounts
    WHERE id = NEW.account_id;

    SELECT code INTO v_role_code
    FROM public.roles
    WHERE id = NEW.role_id;

    IF v_role_code = 'PATIENT' THEN
        IF v_target_type IS DISTINCT FROM 'PATIENT' THEN
            RAISE EXCEPTION 'Staff/non-patient account % cannot be assigned the PATIENT role', NEW.account_id
                USING ERRCODE = '23514';
        END IF;
    ELSIF v_target_type IS DISTINCT FROM 'STAFF' THEN
        RAISE EXCEPTION 'Patient/non-staff account % cannot be assigned a staff role', NEW.account_id
            USING ERRCODE = '23514';
    END IF;

    SELECT account_type INTO v_grantor_type
    FROM public.accounts
    WHERE id = NEW.granted_by;

    IF v_grantor_type IS DISTINCT FROM 'STAFF' THEN
        RAISE EXCEPTION 'Role grantor % must be a STAFF account', NEW.granted_by
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;
