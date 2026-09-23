EXEC sp_rename 'dbo.patients.cccd', 'identification_number', 'COLUMN';
EXEC sp_rename 'dbo.company_employees.cccd', 'identification_number', 'COLUMN';
EXEC sp_rename 'dbo.health_check_import_rows.cccd_snapshot', 'identification_number_snapshot', 'COLUMN';
EXEC sp_rename 'dbo.health_check_records.cccd_snapshot', 'identification_number_snapshot', 'COLUMN';
EXEC sp_rename 'dbo.health_check_records.cccd_issue_date_snapshot', 'identification_number_issue_date_snapshot', 'COLUMN';
EXEC sp_rename 'dbo.health_check_records.cccd_issue_place_snapshot', 'identification_number_issue_place_snapshot', 'COLUMN';

EXEC sp_rename 'dbo.patients.UX_patients_cccd', 'UX_patients_identification_number', 'INDEX';
EXEC sp_rename 'dbo.UQ_company_employees_company_id_cccd',
    'UQ_company_employees_company_id_identification_number', 'OBJECT';
