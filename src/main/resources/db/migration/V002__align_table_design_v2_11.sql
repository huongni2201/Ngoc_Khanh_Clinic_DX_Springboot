-- Preserve the applied V001 history while cutting over to the v2.11 schema.
-- Journey rows may contain clinical operational history. Require a reviewed
-- migration of that history before removing the obsolete state machine.
IF EXISTS (SELECT 1 FROM dbo.journey_events) OR EXISTS (SELECT 1 FROM dbo.journeys)
BEGIN
    THROW 51001, 'Legacy Journey data must be reviewed before the v2.11 cut-over', 1;
END;

DROP TABLE dbo.journey_events;
DROP TABLE dbo.journeys;

EXEC sp_rename 'dbo.patients.identification_number', 'cccd', 'COLUMN';
EXEC sp_rename 'dbo.company_employees.identification_number', 'cccd', 'COLUMN';
EXEC sp_rename 'dbo.health_check_import_rows.identification_number_snapshot', 'cccd_snapshot', 'COLUMN';
EXEC sp_rename 'dbo.health_check_records.identification_number_snapshot', 'cccd_snapshot', 'COLUMN';
EXEC sp_rename 'dbo.health_check_records.identification_number_issue_date_snapshot', 'cccd_issue_date_snapshot', 'COLUMN';
EXEC sp_rename 'dbo.health_check_records.identification_number_issue_place_snapshot', 'cccd_issue_place_snapshot', 'COLUMN';

EXEC sp_rename 'dbo.patients.UX_patients_identification_number', 'UX_patients_cccd', 'INDEX';
EXEC sp_rename 'dbo.UQ_company_employees_company_id_identification_number',
    'UQ_company_employees_company_id_cccd', 'OBJECT';
