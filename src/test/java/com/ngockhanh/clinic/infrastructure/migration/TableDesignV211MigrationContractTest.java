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
            "(?im)\\bcreate\\s+table\\s+dbo\\.([a-z_][a-z0-9_]*)\\b");
    private static final Pattern DROP_TABLE = Pattern.compile(
            "(?im)\\bdrop\\s+table\\s+dbo\\.([a-z_][a-z0-9_]*)\\b");

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
            "companies", "company_employees", "health_check_batches",
            "health_check_batch_services", "health_check_batch_employees", "health_check_records",
            "health_check_batch_employee_services", "health_check_import_jobs", "health_check_import_rows",
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
        Set<String> actualTables = new TreeSet<>();
        var created = CREATE_TABLE.matcher(baseline + "\n" + cutover);
        while (created.find()) actualTables.add(created.group(1).toLowerCase());
        var dropped = DROP_TABLE.matcher(cutover);
        while (dropped.find()) actualTables.remove(dropped.group(1).toLowerCase());

        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    void cutoverRenamesCccdWithoutDroppingPatientData() throws IOException {
        String cutover = readMigration("V002__align_table_design_v2_11.sql").toLowerCase();

        assertThat(cutover)
                .contains("'dbo.patients.identification_number', 'cccd', 'column'")
                .contains("'dbo.company_employees.identification_number', 'cccd', 'column'")
                .contains("'dbo.health_check_records.identification_number_snapshot', 'cccd_snapshot', 'column'")
                .contains("ux_patients_cccd")
                .contains("uq_company_employees_company_id_cccd")
                .contains("drop table dbo.journey_events")
                .contains("drop table dbo.journeys")
                .doesNotContain("public_id", "bigint identity", "newid()", "newsequentialid()");
    }

    private static String readMigration(String fileName) throws IOException {
        try (InputStream stream = TableDesignV211MigrationContractTest.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            assertThat(stream).as("migration must be available on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
