-- Consolidated access-control seed for fresh databases, applied after V001.
-- No accounts or local demo data are created here.

-- Roles, permissions and role grants of the SRS Permission Matrix 4.4 (ADR-0015).
-- One permission per matrix row; sign-in and password rows need no permission.
-- "Restricted n" cells are granted here; footnote n limits are enforced by the use case.
-- Future matrix changes require a new migration.

INSERT INTO public.roles (code, name, description)
VALUES
    ('PATIENT', 'Patient', 'Patient account using the portal for own examinations, reports, prescriptions, appointments and contact details.'),
    ('RECEPTIONIST', 'Receptionist', 'Registers patients, receives outpatients, manages appointments and collects payments.'),
    ('GENERAL_PRACTITIONER', 'General Practitioner', 'Examines outpatients, orders diagnostic services and records diagnoses, conclusions and prescriptions.'),
    ('DIAGNOSTIC_DOCTOR', 'Diagnostic Doctor', 'Performs diagnostic services and records, finalizes, amends and prints diagnostic results.'),
    ('DATA_ENTRY_STAFF', 'Data Entry Staff', 'Prepares participant examination records, enters batch examination data and issues completed records.'),
    ('CLINIC_MANAGER', 'Clinic Manager', 'Manages organizations, health examination batches, participants, clinic master data and operational reports.'),
    ('ADMINISTRATOR', 'Administrator', 'Manages user accounts, roles and permissions, reviews audit logs and submits national health data.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.permissions (code, name, description)
VALUES
    ('USER_ACCOUNT_CREATE', 'Create a user account', 'User Account. UC-005.'),
    ('USER_ACCOUNT_STATUS_UPDATE', 'Deactivate or reactivate a user account', 'User Account. UC-006, UC-007.'),
    ('ROLE_MANAGE', 'View, create, update and deactivate roles', 'Roles and Permissions. UC-008.'),
    ('ROLE_PERMISSION_ASSIGN', 'Assign or remove role permissions', 'Roles and Permissions. UC-009.'),
    ('USER_ROLE_ASSIGN', 'Assign or remove user roles', 'Roles and Permissions. UC-010.'),
    ('PATIENT_VIEW', 'Find patient and view demographic summary', 'Patient. UC-011, UC-012. Restricted: Receptionist footnote 3; General Practitioner footnote 4.'),
    ('PATIENT_CREATE', 'Create a patient profile', 'Patient. UC-013.'),
    ('PATIENT_UPDATE', 'Update personal and contact information', 'Patient. UC-014. Restricted: Receptionist footnote 3.'),
    ('PATIENT_HISTORY_VIEW', 'View previous examinations and clinical history', 'Patient safety and history. UC-015. Restricted: Receptionist footnote 3; General Practitioner footnote 4.'),
    ('PATIENT_SAFETY_UPDATE', 'Record or update allergies, conditions and warnings', 'Patient safety and history. UC-016, UC-017. Restricted: General Practitioner footnote 4.'),
    ('RECEPTION_WORKLIST_VIEW', 'View reception worklist', 'Encounter. UC-018. Restricted: Receptionist footnote 3.'),
    ('ENCOUNTER_CREATE', 'Receive an outpatient and create encounter', 'Encounter. UC-020. Restricted: Receptionist footnote 3.'),
    ('ENCOUNTER_ROOM_CHANGE', 'Change encounter room', 'Encounter. UC-021. Restricted: Receptionist footnote 3.'),
    ('ENCOUNTER_CANCEL', 'Cancel before examination begins', 'Encounter. UC-022. Restricted: Receptionist footnote 3.'),
    ('GENERAL_PRACTITIONER_WORKLIST_VIEW', 'View assigned general practitioner worklist', 'Encounter. UC-023. Restricted: General Practitioner footnote 4.'),
    ('ENCOUNTER_FINDINGS_RECORD', 'Record clinical examination findings', 'Encounter. UC-024. Restricted: General Practitioner footnote 4.'),
    ('PATIENT_SAFETY_VIEW', 'Review relevant history and safety information', 'Patient safety and history. UC-025. Restricted: General Practitioner footnote 4.'),
    ('DIAGNOSTIC_PROGRESS_VIEW', 'View diagnostic progress for the encounter', 'Diagnostic Service Request. UC-026. Restricted: General Practitioner footnote 4.'),
    ('DIAGNOSTIC_RESULT_REVIEW', 'Review available results for the encounter', 'Diagnostic Result. UC-027. Restricted: General Practitioner footnote 4.'),
    ('ENCOUNTER_DIAGNOSIS_RECORD', 'Record final diagnosis', 'Encounter. UC-028. Restricted: General Practitioner footnote 4.'),
    ('ENCOUNTER_CONCLUSION_RECORD', 'Record clinical conclusion and follow-up recommendations', 'Encounter. UC-029. Restricted: General Practitioner footnote 4.'),
    ('ENCOUNTER_COMPLETE', 'Complete the clinical encounter', 'Encounter. UC-030. Restricted: General Practitioner footnote 4.'),
    ('ENCOUNTER_AMEND', 'Amend completed record with traceable history', 'Encounter. UC-031. Restricted: General Practitioner footnote 4.'),
    ('DIAGNOSTIC_SERVICE_REQUEST_CREATE', 'Create a diagnostic service request', 'Diagnostic Service Request. UC-032. Restricted: General Practitioner footnote 4.'),
    ('DIAGNOSTIC_SERVICE_REQUEST_UPDATE', 'Update before processing', 'Diagnostic Service Request. UC-033. Restricted: General Practitioner footnote 4.'),
    ('DIAGNOSTIC_SERVICE_REQUEST_CANCEL', 'Cancel before service begins', 'Diagnostic Service Request. UC-034. Restricted: General Practitioner footnote 4.'),
    ('DIAGNOSTIC_SERVICE_REQUEST_PRINT', 'Print diagnostic order', 'Diagnostic Service Request. UC-035. Restricted: Diagnostic Doctor footnote 5.'),
    ('DIAGNOSTIC_RESULT_VIEW', 'View diagnostic results and status', 'Diagnostic Result. UC-036. Restricted: Diagnostic Doctor footnote 5.'),
    ('DIAGNOSTIC_RESULT_RECORD', 'Record diagnostic result', 'Diagnostic Result. UC-037. Restricted: Diagnostic Doctor footnote 5.'),
    ('DIAGNOSTIC_RESULT_FINALIZE', 'Finalize diagnostic result', 'Diagnostic Result. UC-038. Restricted: Diagnostic Doctor footnote 5.'),
    ('DIAGNOSTIC_RESULT_AMEND', 'Amend finalized result and preserve prior version', 'Diagnostic Result. UC-039. Restricted: Diagnostic Doctor footnote 5.'),
    ('DIAGNOSTIC_RESULT_PRINT', 'Print finalized result for the patient', 'Diagnostic Result. UC-040. Restricted: Diagnostic Doctor footnote 5.'),
    ('PAYMENT_VIEW', 'View encounter charges and payment history', 'Invoice and Payment. UC-041, UC-042. Restricted: Receptionist footnote 3.'),
    ('PAYMENT_COLLECT', 'Collect examination and diagnostic service fees', 'Invoice and Payment. UC-043, UC-044. Restricted: Receptionist footnote 3.'),
    ('PAYMENT_QR_CREATE', 'Generate QR payment request', 'Invoice and Payment. UC-045. Restricted: Receptionist footnote 3.'),
    ('PAYMENT_REFUND', 'Refund payment', 'Invoice and Payment. UC-046. Restricted: Receptionist footnote 3.'),
    ('PAYMENT_CANCEL', 'Cancel pending payment', 'Invoice and Payment. UC-047. Restricted: Receptionist footnote 3.'),
    ('PAYMENT_RECONCILE', 'Reconcile payment', 'Invoice and Payment. UC-048. Restricted: Receptionist footnote 3.'),
    ('PAYMENT_RECEIPT_PRINT', 'Print payment receipt', 'Invoice and Payment. UC-049. Restricted: Receptionist footnote 3.'),
    ('PRESCRIPTION_CREATE', 'Create a prescription', 'Prescription. UC-050. Restricted: General Practitioner footnote 4.'),
    ('PRESCRIPTION_UPDATE', 'Edit before finalization', 'Prescription. UC-051. Restricted: General Practitioner footnote 4.'),
    ('PRESCRIPTION_PRINT', 'Print prescription', 'Prescription. UC-052. Restricted: General Practitioner footnote 4.'),
    ('APPOINTMENT_VIEW', 'View appointment schedule', 'Appointment. UC-053. Restricted: Receptionist footnote 3; General Practitioner footnote 4.'),
    ('APPOINTMENT_CREATE', 'Create appointment or clinical follow-up', 'Appointment. UC-054. Restricted: Receptionist footnote 3; General Practitioner footnote 4.'),
    ('APPOINTMENT_RESCHEDULE', 'Reschedule appointment', 'Appointment. UC-055. Restricted: Receptionist footnote 3.'),
    ('APPOINTMENT_CANCEL', 'Cancel appointment', 'Appointment. UC-056. Restricted: Receptionist footnote 3.'),
    ('APPOINTMENT_NO_SHOW_MARK', 'Mark appointment as no-show', 'Appointment. UC-057. Restricted: Receptionist footnote 3.'),
    ('OWN_ENCOUNTER_VIEW', 'View own examination history and detail', 'Encounter. UC-058, UC-059. Restricted: Patient footnote 2.'),
    ('OWN_DIAGNOSTIC_REPORT_VIEW', 'View own released diagnostic reports', 'Diagnostic Report. UC-060. Restricted: Patient footnote 2.'),
    ('OWN_PRESCRIPTION_VIEW', 'View prescriptions issued to self', 'Prescription. UC-061. Restricted: Patient footnote 2.'),
    ('OWN_APPOINTMENT_VIEW', 'View own appointments', 'Appointment. UC-062. Restricted: Patient footnote 2.'),
    ('OWN_CONTACT_UPDATE', 'Update own contact information', 'Patient. UC-063. Restricted: Patient footnote 2.'),
    ('ORGANIZATION_SEARCH', 'Search organizations', 'Organization. UC-064. Restricted: Clinic Manager footnote 7.'),
    ('ORGANIZATION_VIEW', 'View organization details', 'Organization. UC-065. Restricted: Clinic Manager footnote 7.'),
    ('ORGANIZATION_CREATE', 'Create organization', 'Organization. UC-066. Restricted: Clinic Manager footnote 7.'),
    ('ORGANIZATION_UPDATE', 'Update organization', 'Organization. UC-067. Restricted: Clinic Manager footnote 7.'),
    ('HEALTH_EXAMINATION_BATCH_VIEW', 'View organization batches', 'Health Examination Batch. UC-068. Restricted: Clinic Manager footnote 7.'),
    ('HEALTH_EXAMINATION_BATCH_CREATE', 'Create a health examination batch', 'Health Examination Batch. UC-069. Restricted: Clinic Manager footnote 7.'),
    ('HEALTH_EXAMINATION_BATCH_UPDATE', 'Update examination batch', 'Health Examination Batch. UC-070. Restricted: Clinic Manager footnote 7.'),
    ('HEALTH_EXAMINATION_BATCH_START', 'Start examination batch', 'Health Examination Batch. UC-071. Restricted: Clinic Manager footnote 7.'),
    ('HEALTH_EXAMINATION_BATCH_CLOSE', 'Close examination batch', 'Health Examination Batch. UC-072. Restricted: Clinic Manager footnote 7.'),
    ('HEALTH_EXAMINATION_BATCH_CANCEL', 'Cancel examination batch', 'Health Examination Batch. UC-073. Restricted: Clinic Manager footnote 7.'),
    ('HEALTH_EXAMINATION_BATCH_SERVICE_CONFIGURE', 'Configure batch examination services', 'Health Examination Batch. UC-074. Restricted: Clinic Manager footnote 7.'),
    ('HEALTH_EXAMINATION_BATCH_PRICE_CONFIGURE', 'Configure batch service prices', 'Health Examination Batch. UC-075. Restricted: Clinic Manager footnote 7.'),
    ('PARTICIPANT_VIEW', 'View batch participants', 'Participant. UC-076. Restricted: Clinic Manager footnote 7.'),
    ('PARTICIPANT_TEMPLATE_DOWNLOAD', 'Download import template', 'Participant. UC-077. Restricted: Clinic Manager footnote 7.'),
    ('PARTICIPANT_IMPORT', 'Import participant list', 'Participant. UC-078. Restricted: Clinic Manager footnote 7.'),
    ('PARTICIPANT_UPDATE', 'Update batch participant information', 'Participant. UC-079. Restricted: Clinic Manager footnote 7.'),
    ('PARTICIPANT_REMOVE', 'Remove batch participant', 'Participant. UC-080. Restricted: Clinic Manager footnote 7.'),
    ('PARTICIPANT_EXAMINATION_RECORD_VIEW', 'View participant health examination details', 'Participant Examination Record. UC-081. Restricted: General Practitioner footnote 4; Data Entry Staff footnote 6; Clinic Manager footnote 7.'),
    ('PARTICIPANT_EXAMINATION_RECORD_PREPARE', 'Prepare record and link to patient', 'Participant Examination Record. UC-082. Restricted: Data Entry Staff footnote 6.'),
    ('PARTICIPANT_IDENTITY_CONFLICT_RESOLVE', 'Resolve identity and demographic data conflicts', 'Patient and Participant. UC-083. Restricted: Data Entry Staff footnote 6.'),
    ('PARTICIPANT_EXAMINATION_FORM_CREATE', 'Create health examination form', 'Participant Examination Form. UC-084. Restricted: Data Entry Staff footnote 6.'),
    ('PARTICIPANT_EXAMINATION_FORM_PRINT', 'Print individual or bulk examination forms', 'Participant Examination Form. UC-085, UC-086. Restricted: Data Entry Staff footnote 6.'),
    ('PARTICIPANT_EXAMINATION_DATA_ENTER', 'Enter examination data', 'Participant Examination Record. UC-087. Restricted: Data Entry Staff footnote 6.'),
    ('BATCH_EXAMINATION_DATA_UPLOAD', 'Upload examination data', 'Batch Examination Data. UC-088. Restricted: Data Entry Staff footnote 6.'),
    ('BATCH_EXAMINATION_DATA_IMPORT_REVIEW', 'Review import examination data', 'Batch Examination Data. UC-089. Restricted: Data Entry Staff footnote 6.'),
    ('BATCH_EXAMINATION_DATA_MISSING_REVIEW', 'Review missing examination data', 'Batch Examination Data. UC-090. Restricted: Data Entry Staff footnote 6.'),
    ('BATCH_EXAMINATION_DATA_COMPLETION_VIEW', 'View participant and batch data completion', 'Batch Examination Data. UC-091, UC-092. Restricted: Data Entry Staff footnote 6.'),
    ('PARTICIPANT_EXAMINATION_RECORD_REVIEW', 'Review participant examination data', 'Participant Examination Record. UC-093. Restricted: Clinic Manager footnote 7.'),
    ('PARTICIPANT_EXAMINATION_CONCLUSION_RECORD', 'Record health examination conclusion', 'Participant Examination Record. UC-094. Restricted: Data Entry Staff footnote 6.'),
    ('PARTICIPANT_EXAMINATION_RECORD_COMPLETE', 'Complete participant health examination', 'Participant Examination Record. UC-095. Restricted: Data Entry Staff footnote 6.'),
    ('PARTICIPANT_EXAMINATION_RECORD_ISSUE', 'Issue completed record', 'Participant Examination Record. UC-096. Restricted: Data Entry Staff footnote 6.'),
    ('PARTICIPANT_EXAMINATION_RECORD_AMEND', 'Amend issued record', 'Participant Examination Record. UC-097. Restricted: Data Entry Staff footnote 6.'),
    ('REPORT_PARTICIPANT_EXAMINATION_EXPORT', 'Export participant examination detail report', 'Reports and Analytics. UC-098. Restricted: Data Entry Staff footnote 6.'),
    ('REPORT_EXAMINATION_SERVICE_SUMMARY_EXPORT', 'Export examination service summary report', 'Reports and Analytics. UC-099. Restricted: Data Entry Staff footnote 6.'),
    ('REPORT_BATCH_DATA_COMPLETION_EXPORT', 'Export batch data completion report', 'Reports and Analytics. UC-100. Restricted: Data Entry Staff footnote 6.'),
    ('REPORT_BATCH_FINANCIAL_SUMMARY_EXPORT', 'Export batch financial summary', 'Reports and Analytics. UC-101. Restricted: Clinic Manager footnote 7.'),
    ('DASHBOARD_VIEW', 'View operation dashboard', 'Reports and Analytics. UC-102. Restricted: Clinic Manager footnote 7.'),
    ('ANALYTICS_VISIT_VIEW', 'Analyze visits, encounter outcomes and service utilization', 'Reports and Analytics. UC-103 to UC-113. Restricted: Clinic Manager footnote 7.'),
    ('ANALYTICS_STAFF_ACTIVITY_VIEW', 'Analyze department, room and doctor activity', 'Reports and Analytics. UC-103 to UC-113. Restricted: Clinic Manager footnote 7.'),
    ('ANALYTICS_DIAGNOSTIC_APPOINTMENT_VIEW', 'Analyze diagnostic performance and appointment activity', 'Reports and Analytics. UC-103 to UC-113. Restricted: Clinic Manager footnote 7.'),
    ('ANALYTICS_REVENUE_VIEW', 'Analyze revenue and payment activity', 'Reports and Analytics. UC-103 to UC-113. Restricted: Clinic Manager footnote 7.'),
    ('REPORT_OPERATIONAL_EXPORT', 'Export operational reports', 'Reports and Analytics. UC-103 to UC-113. Restricted: Clinic Manager footnote 7.'),
    ('MASTER_DATA_STAFF_FACILITY_MANAGE', 'Manage staff profiles, departments, specialties and rooms', 'Clinic Master Data. UC-114 to UC-123. Restricted: Clinic Manager footnote 7.'),
    ('MASTER_DATA_SERVICE_CATALOG_MANAGE', 'Manage service catalog and service prices', 'Clinic Master Data. UC-114 to UC-123. Restricted: Clinic Manager footnote 7.'),
    ('MASTER_DATA_LAB_MEDICATION_MANAGE', 'Manage laboratory test catalog and clinic medications', 'Clinic Master Data. UC-114 to UC-123. Restricted: Clinic Manager footnote 7.'),
    ('MASTER_DATA_DOCUMENT_TEMPLATE_MANAGE', 'Manage document templates', 'Clinic Master Data. UC-114 to UC-123. Restricted: Clinic Manager footnote 7.'),
    ('MASTER_DATA_NOTIFICATION_TEMPLATE_MANAGE', 'Manage notification templates', 'Clinic Master Data. UC-114 to UC-123. Restricted: Clinic Manager footnote 7.'),
    ('AUDIT_LOG_VIEW', 'Review system activity audit logs', 'Audit Logs. UC-124.'),
    ('NATIONAL_SUBMISSION_PREPARE', 'Prepare data package', 'National Health Data Submission. UC-125.'),
    ('NATIONAL_SUBMISSION_VALIDATE', 'Validate data package', 'National Health Data Submission. UC-126.'),
    ('NATIONAL_SUBMISSION_SUBMIT', 'Submit data', 'National Health Data Submission. UC-127.'),
    ('NATIONAL_SUBMISSION_STATUS_VIEW', 'View submission status', 'National Health Data Submission. UC-128.'),
    ('NATIONAL_SUBMISSION_ERROR_REVIEW', 'Review submission errors', 'National Health Data Submission. UC-129.'),
    ('NATIONAL_SUBMISSION_RETRY', 'Retry failed submission after resolution', 'National Health Data Submission. UC-130.');

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (
    VALUES
        ('ADMINISTRATOR', 'USER_ACCOUNT_CREATE'),
        ('ADMINISTRATOR', 'USER_ACCOUNT_STATUS_UPDATE'),
        ('ADMINISTRATOR', 'ROLE_MANAGE'),
        ('ADMINISTRATOR', 'ROLE_PERMISSION_ASSIGN'),
        ('ADMINISTRATOR', 'USER_ROLE_ASSIGN'),
        ('RECEPTIONIST', 'PATIENT_VIEW'),
        ('GENERAL_PRACTITIONER', 'PATIENT_VIEW'),
        ('RECEPTIONIST', 'PATIENT_CREATE'),
        ('RECEPTIONIST', 'PATIENT_UPDATE'),
        ('RECEPTIONIST', 'PATIENT_HISTORY_VIEW'),
        ('GENERAL_PRACTITIONER', 'PATIENT_HISTORY_VIEW'),
        ('GENERAL_PRACTITIONER', 'PATIENT_SAFETY_UPDATE'),
        ('RECEPTIONIST', 'RECEPTION_WORKLIST_VIEW'),
        ('RECEPTIONIST', 'ENCOUNTER_CREATE'),
        ('RECEPTIONIST', 'ENCOUNTER_ROOM_CHANGE'),
        ('RECEPTIONIST', 'ENCOUNTER_CANCEL'),
        ('GENERAL_PRACTITIONER', 'GENERAL_PRACTITIONER_WORKLIST_VIEW'),
        ('GENERAL_PRACTITIONER', 'ENCOUNTER_FINDINGS_RECORD'),
        ('GENERAL_PRACTITIONER', 'PATIENT_SAFETY_VIEW'),
        ('GENERAL_PRACTITIONER', 'DIAGNOSTIC_PROGRESS_VIEW'),
        ('GENERAL_PRACTITIONER', 'DIAGNOSTIC_RESULT_REVIEW'),
        ('GENERAL_PRACTITIONER', 'ENCOUNTER_DIAGNOSIS_RECORD'),
        ('GENERAL_PRACTITIONER', 'ENCOUNTER_CONCLUSION_RECORD'),
        ('GENERAL_PRACTITIONER', 'ENCOUNTER_COMPLETE'),
        ('GENERAL_PRACTITIONER', 'ENCOUNTER_AMEND'),
        ('GENERAL_PRACTITIONER', 'DIAGNOSTIC_SERVICE_REQUEST_CREATE'),
        ('GENERAL_PRACTITIONER', 'DIAGNOSTIC_SERVICE_REQUEST_UPDATE'),
        ('GENERAL_PRACTITIONER', 'DIAGNOSTIC_SERVICE_REQUEST_CANCEL'),
        ('DIAGNOSTIC_DOCTOR', 'DIAGNOSTIC_SERVICE_REQUEST_PRINT'),
        ('DIAGNOSTIC_DOCTOR', 'DIAGNOSTIC_RESULT_VIEW'),
        ('DIAGNOSTIC_DOCTOR', 'DIAGNOSTIC_RESULT_RECORD'),
        ('DIAGNOSTIC_DOCTOR', 'DIAGNOSTIC_RESULT_FINALIZE'),
        ('DIAGNOSTIC_DOCTOR', 'DIAGNOSTIC_RESULT_AMEND'),
        ('DIAGNOSTIC_DOCTOR', 'DIAGNOSTIC_RESULT_PRINT'),
        ('RECEPTIONIST', 'PAYMENT_VIEW'),
        ('RECEPTIONIST', 'PAYMENT_COLLECT'),
        ('RECEPTIONIST', 'PAYMENT_QR_CREATE'),
        ('RECEPTIONIST', 'PAYMENT_REFUND'),
        ('RECEPTIONIST', 'PAYMENT_CANCEL'),
        ('RECEPTIONIST', 'PAYMENT_RECONCILE'),
        ('RECEPTIONIST', 'PAYMENT_RECEIPT_PRINT'),
        ('GENERAL_PRACTITIONER', 'PRESCRIPTION_CREATE'),
        ('GENERAL_PRACTITIONER', 'PRESCRIPTION_UPDATE'),
        ('GENERAL_PRACTITIONER', 'PRESCRIPTION_PRINT'),
        ('RECEPTIONIST', 'APPOINTMENT_VIEW'),
        ('GENERAL_PRACTITIONER', 'APPOINTMENT_VIEW'),
        ('RECEPTIONIST', 'APPOINTMENT_CREATE'),
        ('GENERAL_PRACTITIONER', 'APPOINTMENT_CREATE'),
        ('RECEPTIONIST', 'APPOINTMENT_RESCHEDULE'),
        ('RECEPTIONIST', 'APPOINTMENT_CANCEL'),
        ('RECEPTIONIST', 'APPOINTMENT_NO_SHOW_MARK'),
        ('PATIENT', 'OWN_ENCOUNTER_VIEW'),
        ('PATIENT', 'OWN_DIAGNOSTIC_REPORT_VIEW'),
        ('PATIENT', 'OWN_PRESCRIPTION_VIEW'),
        ('PATIENT', 'OWN_APPOINTMENT_VIEW'),
        ('PATIENT', 'OWN_CONTACT_UPDATE'),
        ('CLINIC_MANAGER', 'ORGANIZATION_SEARCH'),
        ('CLINIC_MANAGER', 'ORGANIZATION_VIEW'),
        ('CLINIC_MANAGER', 'ORGANIZATION_CREATE'),
        ('CLINIC_MANAGER', 'ORGANIZATION_UPDATE'),
        ('CLINIC_MANAGER', 'HEALTH_EXAMINATION_BATCH_VIEW'),
        ('CLINIC_MANAGER', 'HEALTH_EXAMINATION_BATCH_CREATE'),
        ('CLINIC_MANAGER', 'HEALTH_EXAMINATION_BATCH_UPDATE'),
        ('CLINIC_MANAGER', 'HEALTH_EXAMINATION_BATCH_START'),
        ('CLINIC_MANAGER', 'HEALTH_EXAMINATION_BATCH_CLOSE'),
        ('CLINIC_MANAGER', 'HEALTH_EXAMINATION_BATCH_CANCEL'),
        ('CLINIC_MANAGER', 'HEALTH_EXAMINATION_BATCH_SERVICE_CONFIGURE'),
        ('CLINIC_MANAGER', 'HEALTH_EXAMINATION_BATCH_PRICE_CONFIGURE'),
        ('CLINIC_MANAGER', 'PARTICIPANT_VIEW'),
        ('CLINIC_MANAGER', 'PARTICIPANT_TEMPLATE_DOWNLOAD'),
        ('CLINIC_MANAGER', 'PARTICIPANT_IMPORT'),
        ('CLINIC_MANAGER', 'PARTICIPANT_UPDATE'),
        ('CLINIC_MANAGER', 'PARTICIPANT_REMOVE'),
        ('GENERAL_PRACTITIONER', 'PARTICIPANT_EXAMINATION_RECORD_VIEW'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_EXAMINATION_RECORD_VIEW'),
        ('CLINIC_MANAGER', 'PARTICIPANT_EXAMINATION_RECORD_VIEW'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_EXAMINATION_RECORD_PREPARE'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_IDENTITY_CONFLICT_RESOLVE'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_EXAMINATION_FORM_CREATE'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_EXAMINATION_FORM_PRINT'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_EXAMINATION_DATA_ENTER'),
        ('DATA_ENTRY_STAFF', 'BATCH_EXAMINATION_DATA_UPLOAD'),
        ('DATA_ENTRY_STAFF', 'BATCH_EXAMINATION_DATA_IMPORT_REVIEW'),
        ('DATA_ENTRY_STAFF', 'BATCH_EXAMINATION_DATA_MISSING_REVIEW'),
        ('DATA_ENTRY_STAFF', 'BATCH_EXAMINATION_DATA_COMPLETION_VIEW'),
        ('CLINIC_MANAGER', 'PARTICIPANT_EXAMINATION_RECORD_REVIEW'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_EXAMINATION_CONCLUSION_RECORD'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_EXAMINATION_RECORD_COMPLETE'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_EXAMINATION_RECORD_ISSUE'),
        ('DATA_ENTRY_STAFF', 'PARTICIPANT_EXAMINATION_RECORD_AMEND'),
        ('DATA_ENTRY_STAFF', 'REPORT_PARTICIPANT_EXAMINATION_EXPORT'),
        ('DATA_ENTRY_STAFF', 'REPORT_EXAMINATION_SERVICE_SUMMARY_EXPORT'),
        ('DATA_ENTRY_STAFF', 'REPORT_BATCH_DATA_COMPLETION_EXPORT'),
        ('CLINIC_MANAGER', 'REPORT_BATCH_FINANCIAL_SUMMARY_EXPORT'),
        ('CLINIC_MANAGER', 'DASHBOARD_VIEW'),
        ('CLINIC_MANAGER', 'ANALYTICS_VISIT_VIEW'),
        ('CLINIC_MANAGER', 'ANALYTICS_STAFF_ACTIVITY_VIEW'),
        ('CLINIC_MANAGER', 'ANALYTICS_DIAGNOSTIC_APPOINTMENT_VIEW'),
        ('CLINIC_MANAGER', 'ANALYTICS_REVENUE_VIEW'),
        ('CLINIC_MANAGER', 'REPORT_OPERATIONAL_EXPORT'),
        ('CLINIC_MANAGER', 'MASTER_DATA_STAFF_FACILITY_MANAGE'),
        ('CLINIC_MANAGER', 'MASTER_DATA_SERVICE_CATALOG_MANAGE'),
        ('CLINIC_MANAGER', 'MASTER_DATA_LAB_MEDICATION_MANAGE'),
        ('CLINIC_MANAGER', 'MASTER_DATA_DOCUMENT_TEMPLATE_MANAGE'),
        ('CLINIC_MANAGER', 'MASTER_DATA_NOTIFICATION_TEMPLATE_MANAGE'),
        ('ADMINISTRATOR', 'AUDIT_LOG_VIEW'),
        ('ADMINISTRATOR', 'NATIONAL_SUBMISSION_PREPARE'),
        ('ADMINISTRATOR', 'NATIONAL_SUBMISSION_VALIDATE'),
        ('ADMINISTRATOR', 'NATIONAL_SUBMISSION_SUBMIT'),
        ('ADMINISTRATOR', 'NATIONAL_SUBMISSION_STATUS_VIEW'),
        ('ADMINISTRATOR', 'NATIONAL_SUBMISSION_ERROR_REVIEW'),
        ('ADMINISTRATOR', 'NATIONAL_SUBMISSION_RETRY')
) AS grant_matrix(role_code, permission_code)
JOIN public.roles r ON r.code = grant_matrix.role_code
JOIN public.permissions p ON p.code = grant_matrix.permission_code;

-- Retain superseded roster codes in the catalog without granting them.
-- Runtime endpoints use the SRS Participant permissions seeded above (ADR-0015).
INSERT INTO public.permissions (code, name, description)
VALUES
    ('HEALTH_EXAMINATION_PARTICIPANT_READ',
     'Read health examination Participants',
     'List the Participant roster of a health examination batch.'),
    ('HEALTH_EXAMINATION_PARTICIPANT_IMPORT',
     'Import health examination Participants',
     'Download the Participant Excel template and add Participants to a health examination batch.')
ON CONFLICT (code) DO NOTHING;

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

-- Manual creation and reactivation have separate endpoint permissions.
INSERT INTO public.permissions (code, name, description)
VALUES
    ('PARTICIPANT_CREATE', 'Create a batch participant', 'Add one Participant manually to a health examination batch.'),
    ('PARTICIPANT_REACTIVATE', 'Reactivate a batch participant', 'Return a cancelled Participant to the active roster.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
    ON permission.code IN ('PARTICIPANT_CREATE', 'PARTICIPANT_REACTIVATE')
WHERE role.code = 'CLINIC_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Permissions of the examination detail matrix and the payment report of a health examination batch:
--   HEALTH_EXAMINATION_SERVICE_READ       view the examination detail matrix and export it to Excel
--   HEALTH_EXAMINATION_SERVICE_RECONCILE  import an examination detail Excel file
--   HEALTH_EXAMINATION_REPORT_READ        view the payment report and export it to Word
-- Only the permission catalog is added. Roles that already exist receive the grants below; roles
-- created later must be granted explicitly. No permission is granted to every staff account.

INSERT INTO public.permissions (code, name, description)
VALUES
    ('HEALTH_EXAMINATION_SERVICE_READ',
     'Read health examination service details',
     'View the Participant by service matrix of a health examination batch and export it to Excel.'),
    ('HEALTH_EXAMINATION_SERVICE_RECONCILE',
     'Reconcile health examination services',
     'Import an Excel file that records the services performed for Participants of a batch.'),
    ('HEALTH_EXAMINATION_REPORT_READ',
     'Read health examination payment report',
     'View the payment summary of a health examination batch and export it to Word.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
  ON permission.code IN (
      'HEALTH_EXAMINATION_SERVICE_READ',
      'HEALTH_EXAMINATION_SERVICE_RECONCILE',
      'HEALTH_EXAMINATION_REPORT_READ')
WHERE role.code IN ('ADMIN', 'CLINIC_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
