ALTER TABLE public.health_examination_batch_participants
    ADD COLUMN full_name_snapshot varchar(200),
    ADD COLUMN date_of_birth_snapshot date,
    ADD COLUMN sex_snapshot varchar(16),
    ADD COLUMN identification_number_snapshot varchar(20),
    ADD COLUMN identification_number_issue_date_snapshot date,
    ADD COLUMN identification_number_issue_place_snapshot varchar(200),
    ADD COLUMN ethnicity_snapshot varchar(100),
    ADD COLUMN subject_type_snapshot varchar(100),
    ADD COLUMN payer_source_snapshot varchar(150),
    ADD COLUMN blood_group_snapshot varchar(16),
    ADD COLUMN phone_snapshot varchar(30),
    ADD COLUMN province_snapshot varchar(150),
    ADD COLUMN ward_snapshot varchar(150),
    ADD COLUMN address_detail_snapshot varchar(500),
    ADD COLUMN administrative_occupation_snapshot varchar(200),
    ADD COLUMN workplace_or_school_snapshot varchar(300),
    ADD COLUMN health_examination_reason_snapshot varchar(500);

UPDATE public.health_examination_batch_participants
SET full_name_snapshot = administrative_snapshot_json::jsonb ->> 'fullName',
    date_of_birth_snapshot = NULLIF(administrative_snapshot_json::jsonb ->> 'dateOfBirth', '')::date,
    sex_snapshot = administrative_snapshot_json::jsonb ->> 'sex',
    identification_number_snapshot = administrative_snapshot_json::jsonb -> 'identificationNumber' ->> 'value',
    identification_number_issue_date_snapshot = NULLIF(
        administrative_snapshot_json::jsonb ->> 'identificationNumberIssueDate', '')::date,
    identification_number_issue_place_snapshot = administrative_snapshot_json::jsonb ->> 'identificationNumberIssuePlace',
    ethnicity_snapshot = administrative_snapshot_json::jsonb ->> 'ethnicity',
    subject_type_snapshot = administrative_snapshot_json::jsonb ->> 'subjectType',
    payer_source_snapshot = administrative_snapshot_json::jsonb ->> 'payerSource',
    blood_group_snapshot = administrative_snapshot_json::jsonb ->> 'bloodGroup',
    phone_snapshot = administrative_snapshot_json::jsonb ->> 'phone',
    province_snapshot = administrative_snapshot_json::jsonb ->> 'province',
    ward_snapshot = administrative_snapshot_json::jsonb ->> 'ward',
    address_detail_snapshot = administrative_snapshot_json::jsonb ->> 'addressDetail',
    administrative_occupation_snapshot = administrative_snapshot_json::jsonb ->> 'occupation',
    workplace_or_school_snapshot = administrative_snapshot_json::jsonb ->> 'workplaceOrSchool',
    health_examination_reason_snapshot = administrative_snapshot_json::jsonb ->> 'healthExaminationReason';

ALTER TABLE public.health_examination_batch_participants
    ALTER COLUMN full_name_snapshot SET NOT NULL,
    ALTER COLUMN date_of_birth_snapshot SET NOT NULL,
    ALTER COLUMN sex_snapshot SET NOT NULL,
    ALTER COLUMN identification_number_snapshot SET NOT NULL,
    DROP COLUMN administrative_snapshot_json;
