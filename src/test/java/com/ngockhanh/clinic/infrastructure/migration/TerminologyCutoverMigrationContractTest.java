package com.ngockhanh.clinic.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TerminologyCutoverMigrationContractTest {
    @Test
    void organizationAndParticipantCutoverPreservesRowsAndRenamesReferences() throws IOException {
        String migration = readMigration("V007__rename_company_terms_to_organization_participant.sql").toLowerCase();

        assertThat(migration)
                .contains("alter table public.companies rename to organizations")
                .contains("companies", "organizations")
                .contains("company_employees", "health_examination_participants")
                .contains("health_examination_batch_employees", "health_examination_batch_participants")
                .contains("company_id", "organization_id")
                .contains("employee_code", "participant_code")
                .doesNotContain("drop table", "drop column", "delete from");
    }

    private static String readMigration(String fileName) throws IOException {
        try (InputStream stream = TerminologyCutoverMigrationContractTest.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            assertThat(stream).as("migration must be available on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
