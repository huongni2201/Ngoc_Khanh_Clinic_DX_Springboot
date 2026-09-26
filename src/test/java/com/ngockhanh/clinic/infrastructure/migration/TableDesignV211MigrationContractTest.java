package com.ngockhanh.clinic.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class TableDesignV211MigrationContractTest {
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?im)\\bcreate\\s+table\\s+public\\.([a-z_][a-z0-9_]*)\\b");

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
    void freshInstallUsesOneVersionedMigration() throws IOException {
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            assertThat(migrations
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.+\\.sql")))
                    .containsExactly("V001__create_final_schema.sql");
        }
    }

    @Test
    void initialMigrationDeclaresExactlyTheFinalSchemaTables() throws IOException {
        String migration = readMigration("V001__create_final_schema.sql");
        Set<String> actualTables = new TreeSet<>();
        var created = CREATE_TABLE.matcher(migration);
        while (created.find()) actualTables.add(created.group(1).toLowerCase());

        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
        assertThat(migration)
                .doesNotContain("CREATE TABLE public.journeys", "CREATE TABLE public.journey_events")
                .doesNotContain("RENAME COLUMN", "RENAME TO", "DROP TABLE", "DROP COLUMN", "ALTER TABLE");
    }

    @Test
    void initialMigrationContainsFinalIdentityReleaseAndLifecycleContracts() throws IOException {
        String migration = readMigration("V001__create_final_schema.sql").toLowerCase();

        assertThat(migration)
                .contains("identification_number varchar(20) not null")
                .contains("released_to_patient_at timestamptz(3) null")
                .contains("released_to_patient_by_user_id uuid null")
                .contains("fk_lab_results_released_to_patient_by_user_id")
                .contains("fk_diagnostic_reports_released_to_patient_by_user_id")
                .contains("ck_users_principal_type")
                .contains("ck_encounters_status")
                .contains("ck_service_requests_status")
                .contains("ck_payment_authorizations_status")
                .contains("ck_payments_status")
                .contains("ck_health_examination_batches_status")
                .contains("ck_health_examination_records_status");
    }

    @Test
    void coreBusinessUniquenessInvariantsRemainDatabaseEnforced() throws IOException {
        String migration = readMigration("V001__create_final_schema.sql").toLowerCase();

        assertThat(migration)
                .contains("ux_patients_identification_number")
                .contains("uq_encounters_encounter_code")
                .contains("ux_encounter_assignments_active")
                .contains("uq_payment_authorizations_service_request_id")
                .contains("ux_health_examination_records_shs_code")
                .contains("ux_health_examination_records_encounter")
                .contains("ux_health_examination_records_batch_participant")
                .contains("ux_health_examination_batch_participant_services_request");
    }

    private static String readMigration(String fileName) throws IOException {
        try (InputStream stream = TableDesignV211MigrationContractTest.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            assertThat(stream).as("migration must be available on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
