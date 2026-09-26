package com.ngockhanh.clinic.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TableDesignV211MigrationContractTest {
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?im)\\bcreate\\s+table\\s+public\\.([a-z_][a-z0-9_]*)\\b");
    private static final Pattern DROP_TABLE = Pattern.compile(
            "(?im)\\bdrop\\s+table\\s+public\\.([a-z_][a-z0-9_]*)\\b");

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "patients", "patient_allergies", "patient_conditions",
            "departments", "rooms", "staff", "staff_department_assignments",
            "diagnosis_catalog", "services", "service_prices", "medications",
            "document_templates", "document_template_versions", "document_template_fields",
            "service_template_mappings", "generated_documents", "generated_document_service_requests",
            "appointments", "encounters", "encounter_assignments",
            "vital_signs", "clinical_notes", "encounter_diagnoses",
            "order_rounds", "service_requests",
            "payment_authorizations", "invoices", "invoice_items", "invoice_adjustments", "payments",
            "organizations", "health_examination_participants", "health_examination_batches",
            "health_examination_batch_services", "health_examination_batch_participants", "health_examination_records",
            "health_examination_batch_participant_services", "health_examination_import_jobs", "health_examination_import_rows",
            "specimens", "specimen_service_requests", "lab_panels", "analytes", "lab_panel_items",
            "analyte_reference_ranges", "lab_results", "lab_result_values", "imaging_studies",
            "diagnostic_reports", "file_attachments",
            "prescriptions", "prescription_items",
            "users", "roles", "permissions", "user_roles", "role_permissions",
            "notifications", "notification_attempts", "audit_logs",
            "integration_endpoints", "external_code_mappings", "integration_messages",
            "idempotency_keys", "outbox_events"
    );

    @Test
    void migrationSequenceDeclaresExactlyTheLatestSchemaTables() throws IOException {
        String baseline = readMigration("V001__create_table_design_v2_10.sql");
        String cutover = readMigration("V002__align_table_design_v2_11.sql");
        String resultRelease = readMigration("V003__complete_result_release_v2_11.sql");
        String lifecycleChecks = readMigration("V004__add_core_lifecycle_checks.sql");
        String identifierNames = readMigration("V005__restore_identification_number_column_names.sql");
        String terminologyCutover = readMigration("V006__rename_health_check_to_health_examination.sql");
        String terminologyParticipantCutover = readMigration("V007__rename_company_terms_to_organization_participant.sql");
        Set<String> actualTables = new TreeSet<>();
        var created = CREATE_TABLE.matcher(baseline + "\n" + cutover + "\n" + resultRelease + "\n"
                + lifecycleChecks + "\n" + identifierNames);
        while (created.find()) actualTables.add(created.group(1).toLowerCase());
        var dropped = DROP_TABLE.matcher(cutover);
        while (dropped.find()) actualTables.remove(dropped.group(1).toLowerCase());
        actualTables = actualTables.stream()
                .map(TableDesignV211MigrationContractTest::renameHealthCheckTable)
                .map(TableDesignV211MigrationContractTest::renameOrganizationParticipantTables)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
        assertThat(terminologyCutover)
                .contains("health_check_batches", "health_examination_batches")
                .contains("health_check_records", "health_examination_records");
        assertThat(terminologyParticipantCutover)
                .contains("company_employees", "health_examination_participants")
                .contains("health_examination_batch_employees", "health_examination_batch_participants");
    }

    @Test
    void resultReleaseMigrationAddsVersionScopedFieldsAndUserForeignKeys() throws IOException {
        String resultRelease = readMigration("V003__complete_result_release_v2_11.sql").toLowerCase();

        assertThat(resultRelease)
                .contains("released_to_patient_at timestamptz(3) null")
                .contains("released_to_patient_by_user_id uuid null")
                .contains("fk_lab_results_released_to_patient_by_user_id")
                .contains("fk_diagnostic_reports_released_to_patient_by_user_id")
                .contains("references public.users(id)");
    }

    @Test
    void lifecycleMigrationConstrainsOnlyDocumentedStatuses() throws IOException {
        String lifecycleChecks = readMigration("V004__add_core_lifecycle_checks.sql").toLowerCase();

        assertThat(lifecycleChecks)
                .contains("ck_encounters_status")
                .contains("'prepared', 'in_progress', 'completed', 'canceled'")
                .contains("ck_service_requests_status")
                .contains("'ordered', 'in_progress', 'completed', 'canceled'")
                .contains("ck_payment_authorizations_status")
                .contains("'not_required', 'pending', 'authorized', 'waived', 'revoked'")
                .contains("ck_payments_status")
                .contains("'pending', 'confirmed', 'failed', 'partially_refunded', 'refunded'")
                .contains("ck_health_check_batches_status")
                .contains("'draft', 'ready', 'in_progress', 'result_processing', 'finalized', 'closed', 'canceled'")
                .contains("ck_health_check_records_status")
                .contains("'active', 'completed', 'canceled', 'replaced'");
    }

    @Test
    void coreBusinessUniquenessInvariantsRemainDatabaseEnforced() throws IOException {
        String baselineAndCutover = (readMigration("V001__create_table_design_v2_10.sql") + "\n"
                + readMigration("V002__align_table_design_v2_11.sql") + "\n"
                + readMigration("V005__restore_identification_number_column_names.sql")).toLowerCase();

        assertThat(baselineAndCutover)
                .contains("ux_patients_identification_number")
                .contains("uq_encounters_encounter_code")
                .contains("ux_encounter_assignments_active")
                .contains("uq_payment_authorizations_service_request_id")
                .contains("ux_health_check_records_shs_code")
                .contains("ux_health_check_records_encounter")
                .contains("ux_health_check_records_batch_employee")
                .contains("ux_hcbes_service_request")
                .contains("ck_users_principal_type");
    }

    @Test
    void cutoverRenamesCccdWithoutDroppingPatientData() throws IOException {
        String cutover = readMigration("V002__align_table_design_v2_11.sql").toLowerCase();

        assertThat(cutover)
                .contains("alter table public.patients rename column identification_number to cccd")
                .contains("alter table public.company_employees rename column identification_number to cccd")
                .contains("alter table public.health_check_records rename column identification_number_snapshot to cccd_snapshot")
                .contains("ux_patients_cccd")
                .contains("uq_company_employees_company_id_cccd")
                .contains("drop table public.journey_events")
                .contains("drop table public.journeys")
                .doesNotContain("public_id", "bigint identity", "newid()", "newsequentialid()");
    }

    @Test
    void finalIdentifierMigrationRestoresTechnicalNamesWithoutDroppingData() throws IOException {
        String identifierNames = readMigration("V005__restore_identification_number_column_names.sql").toLowerCase();

        assertThat(identifierNames)
                .contains("alter table public.patients rename column cccd to identification_number")
                .contains("alter table public.company_employees rename column cccd to identification_number")
                .contains("alter table public.health_check_import_rows rename column cccd_snapshot to identification_number_snapshot")
                .contains("alter table public.health_check_records rename column cccd_snapshot to identification_number_snapshot")
                .contains("ux_patients_identification_number")
                .contains("uq_company_employees_company_id_identification_number")
                .doesNotContain("drop table", "drop column", "delete from");
    }

    @Test
    void terminologyMigrationRenamesSchemaWithoutDroppingData() throws IOException {
        String terminologyCutover = readMigration("V006__rename_health_check_to_health_examination.sql").toLowerCase();

        assertThat(terminologyCutover)
                .contains("alter table public.health_check_batches rename to health_examination_batches")
                .contains("health_check_records", "health_examination_records")
                .contains("health_check_eligible", "health_examination_eligible")
                .contains("doctor_staff_id", "physician_staff_id")
                .doesNotContain("drop table", "drop column", "delete from");
    }

    @Test
    void organizationParticipantMigrationRenamesSchemaWithoutDroppingData() throws IOException {
        String terminologyCutover = readMigration("V007__rename_company_terms_to_organization_participant.sql").toLowerCase();

        assertThat(terminologyCutover)
                .contains("alter table public.companies rename to organizations")
                .contains("company_employees", "health_examination_participants")
                .contains("company_id", "organization_id")
                .contains("employee_code", "participant_code")
                .doesNotContain("drop table", "drop column", "delete from");
    }

    private static String renameHealthCheckTable(String tableName) {
        return tableName.replace("health_check_", "health_examination_");
    }

    private static String renameOrganizationParticipantTables(String tableName) {
        return tableName
                .replace("companies", "organizations")
                .replace("company_employees", "health_examination_participants")
                .replace("health_examination_batch_employee_services", "health_examination_batch_participant_services")
                .replace("health_examination_batch_employees", "health_examination_batch_participants");
    }

    private static String readMigration(String fileName) throws IOException {
        try (InputStream stream = TableDesignV211MigrationContractTest.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            assertThat(stream).as("migration must be available on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
