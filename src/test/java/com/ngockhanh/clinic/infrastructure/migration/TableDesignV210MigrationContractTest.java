package com.ngockhanh.clinic.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TableDesignV210MigrationContractTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?im)\\bcreate\\s+table\\s+dbo\\.([a-z_][a-z0-9_]*)\\b");

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "patients", "patient_allergies", "patient_conditions",
            "departments", "rooms", "staff", "staff_department_assignments",
            "diagnosis_catalog", "services", "service_prices", "medications",
            "document_templates", "document_template_versions", "document_template_fields",
            "service_template_mappings", "generated_documents", "generated_document_service_requests",
            "appointments", "encounters", "encounter_assignments", "journeys", "journey_events",
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
            "notifications", "notification_attempts",
            "audit_logs",
            "integration_endpoints", "external_code_mappings", "integration_messages",
            "idempotency_keys", "outbox_events"
    );

    @Test
    void migrationDeclaresExactlyTheLatestSchemaTables() throws IOException {
        String migration = readMigration();
        Set<String> actualTables = new TreeSet<>();
        var matcher = CREATE_TABLE.matcher(migration);
        while (matcher.find()) {
            actualTables.add(matcher.group(1).toLowerCase());
        }

        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    void migrationDoesNotContainLegacyMvpIdentifiers() throws IOException {
        String migration = readMigration().toLowerCase();

        assertThat(migration)
                .doesNotContain("enterprises")
                .doesNotContain("cccd")
                .doesNotContain("user_accounts")
                .doesNotContain("nkc")
                .doesNotContain("public_id")
                .doesNotContain("bigint identity");
    }

    private static String readMigration() throws IOException {
        try (InputStream stream = TableDesignV210MigrationContractTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V001__create_table_design_v2_10.sql")) {
            assertThat(stream)
                    .as("latest schema migration must be available on the test classpath")
                    .isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
