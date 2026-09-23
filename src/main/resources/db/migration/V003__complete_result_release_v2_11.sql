ALTER TABLE dbo.lab_results
ADD released_to_patient_at datetime2(3) NULL,
    released_to_patient_by_user_id uniqueidentifier NULL;

ALTER TABLE dbo.lab_results
ADD CONSTRAINT FK_lab_results_released_to_patient_by_user_id
FOREIGN KEY (released_to_patient_by_user_id)
REFERENCES dbo.users(id);

ALTER TABLE dbo.diagnostic_reports
ADD released_to_patient_at datetime2(3) NULL,
    released_to_patient_by_user_id uniqueidentifier NULL;

ALTER TABLE dbo.diagnostic_reports
ADD CONSTRAINT FK_diagnostic_reports_released_to_patient_by_user_id
FOREIGN KEY (released_to_patient_by_user_id)
REFERENCES dbo.users(id);
