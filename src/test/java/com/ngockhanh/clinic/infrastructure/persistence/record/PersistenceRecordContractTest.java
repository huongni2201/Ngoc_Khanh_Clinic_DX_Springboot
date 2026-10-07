package com.ngockhanh.clinic.infrastructure.persistence.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PersistenceRecordContractTest {
  private static final Pattern TABLE =
      Pattern.compile(
          "CREATE TABLE public\\.([a-z_]+) \\((.*?)^\\);", Pattern.DOTALL | Pattern.MULTILINE);
  private static final Pattern COLUMN =
      Pattern.compile(
          "^    ([a-z_]+) (uuid|varchar(?:\\(\\d+\\))?|text|boolean|bigint|integer|smallint|numeric\\([\\d,]+\\)|date|timestamptz\\(3\\)|jsonb|bytea|inet)(.*)$",
          Pattern.MULTILINE);
  private static final Pattern ADD_COLUMN =
      Pattern.compile(
          "ALTER TABLE public\\.([a-z_]+)\\s+ADD COLUMN ([a-z_]+) ([^;]*);", Pattern.DOTALL);
  private static final String OWNERS =
      """
      departments catalog DepartmentRecord
      rooms catalog RoomRecord
      specialties catalog SpecialtyRecord
      services catalog ServiceRecord
      medicines catalog MedicineRecord
      lab_tests catalog LabTestRecord
      lab_analytes catalog LabAnalyteRecord
      lab_test_analytes catalog LabTestAnalyteRecord
      patients patient PatientRecord
      patient_allergies patient PatientAllergyRecord
      patient_conditions patient PatientConditionRecord
      encounters encounter EncounterRecord
      vital_signs clinical VitalSignRecord
      encounter_assessments clinical EncounterAssessmentRecord
      encounter_assessment_versions clinical EncounterAssessmentVersionRecord
      diagnoses clinical DiagnosisRecord
      order_rounds clinical OrderRoundRecord
      service_requests clinical ServiceRequestRecord
      result_series diagnostics ResultSeriesRecord
      result_versions diagnostics ResultVersionRecord
      lab_result_items diagnostics LabResultItemRecord
      invoices billing InvoiceRecord
      invoice_lines billing InvoiceLineRecord
      payments billing PaymentRecord
      refunds billing RefundRecord
      service_authorizations billing ServiceAuthorizationRecord
      prescriptions prescription PrescriptionRecord
      prescription_versions prescription PrescriptionVersionRecord
      prescription_items prescription PrescriptionItemRecord
      appointments appointment AppointmentRecord
      files document FileRecord
      templates document TemplateRecord
      template_versions document TemplateVersionRecord
      service_template_mappings document ServiceTemplateMappingRecord
      issued_representations document IssuedRepresentationRecord
      system_connections integration SystemConnectionRecord
      external_code_mappings integration ExternalCodeMappingRecord
      health_data_submissions integration HealthDataSubmissionRecord
      idempotency_keys integration IdempotencyKeyRecord
      outbox_events integration OutboxEventRecord
      import_jobs integration ImportJobRecord
      import_rows integration ImportRowRecord
      result_releases portal ResultReleaseRecord
      document_releases portal DocumentReleaseRecord
      notification_batches notification NotificationBatchRecord
      notifications notification NotificationRecord
      notification_attempts notification NotificationAttemptRecord
      staff_members accesscontrol StaffMemberRecord
      roles accesscontrol RoleRecord
      permissions accesscontrol PermissionRecord
      accounts accesscontrol AccountRecord
      account_roles accesscontrol AccountRoleRecord
      role_permissions accesscontrol RolePermissionRecord
      audit_events audit AuditEventRecord
      organizations healthexamination OrganizationRecord
      health_examination_batches healthexamination HealthExaminationBatchRecord
      health_examination_batch_days healthexamination HealthExaminationBatchDayRecord
      health_examination_batch_services healthexamination HealthExaminationBatchServiceRecord
      health_examination_batch_participants healthexamination HealthExaminationBatchParticipantRecord
      health_examination_participant_services healthexamination HealthExaminationParticipantServiceRecord
      health_examination_records healthexamination HealthExaminationRecordRecord
      health_examination_record_snapshots healthexamination HealthExaminationRecordSnapshotRecord
      health_examination_record_versions healthexamination HealthExaminationRecordVersionRecord
      health_examination_record_version_items healthexamination HealthExaminationRecordVersionItemRecord
      """;

  @Test
  void allTablesHaveOneRecordWithTheirActualSqlColumnOrderAndTypes() throws Exception {
    String sql =
        Files.readString(
            Path.of("src/main/resources/db/migration/V001__create_clean_slate_schema.sql"));
    var tables = new HashMap<String, String>();
    TABLE.matcher(sql).results().forEach(match -> tables.put(match.group(1), match.group(2)));
    assertThat(tables).hasSize(64);
    // Later migrations only append columns, so a record is the baseline columns plus each
    // "ALTER TABLE ... ADD COLUMN" in version order.
    try (var migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
      for (Path migration :
          migrations
              .filter(path -> path.getFileName().toString().matches("V(?!001__)\\d+__.*\\.sql"))
              .sorted()
              .toList()) {
        String text = Files.readString(migration).replaceAll("(?m)^--.*$", "");
        for (var add : ADD_COLUMN.matcher(text).results().toList()) {
          assertThat(tables).as("table altered in %s", migration).containsKey(add.group(1));
          tables.merge(
              add.group(1),
              "    " + add.group(2) + " " + add.group(3).strip() + "\n",
              (body, column) -> body + column);
        }
      }
    }
    var mapped = new HashSet<String>();
    var expectedPaths = new HashSet<Path>();
    for (String entry : OWNERS.strip().split("\\R")) {
      String[] parts = entry.strip().split(" ");
      assertThat(mapped.add(parts[0])).as("one owner for %s", parts[0]).isTrue();
      String className =
          "com.ngockhanh.clinic." + parts[1] + ".infrastructure.persistence.record." + parts[2];
      Class<?> type = Class.forName(className);
      assertThat(type.isRecord()).as(className).isTrue();
      expectedPaths.add(Path.of("src/main/java/" + className.replace('.', '/') + ".java"));
      var columns = COLUMN.matcher(tables.get(parts[0])).results().toList();
      var fields = type.getRecordComponents();
      assertThat(Arrays.stream(fields).map(field -> snakeCase(field.getName())).toList())
          .as(parts[0])
          .containsExactlyElementsOf(columns.stream().map(match -> match.group(1)).toList());
      for (int i = 0; i < fields.length; i++) {
        var column = columns.get(i);
        assertThat(fields[i].getType())
            .as(parts[0] + "." + column.group(1))
            .isEqualTo(javaType(column.group(2), column.group(3)));
      }
    }
    assertThat(mapped).containsExactlyInAnyOrderElementsOf(tables.keySet());
    try (var files = Files.walk(Path.of("src/main/java/com/ngockhanh/clinic"))) {
      assertThat(
              files
                  .filter(
                      path ->
                          path.toString().endsWith(".java")
                              && path.getParent().getFileName().toString().equals("record")
                              && path.toString().contains("persistence"))
                  .toList())
          .containsExactlyInAnyOrderElementsOf(expectedPaths);
    }
  }

  @Test
  void everyTableRecordExposesABuilder() throws ClassNotFoundException {
    for (String entry : OWNERS.strip().split("\\R")) {
      String[] parts = entry.strip().split(" ");
      String className =
          "com.ngockhanh.clinic." + parts[1] + ".infrastructure.persistence.record." + parts[2];
      assertThat(Class.forName(className).getDeclaredMethods())
          .as(className)
          .anyMatch(method -> method.getName().equals("builder"));
    }
  }

  private static String snakeCase(String name) {
    return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }

  private static Class<?> javaType(String sqlType, String definition) {
    boolean required = definition.contains("NOT NULL") || definition.contains("PRIMARY KEY");
    if (sqlType.equals("uuid")) return UUID.class;
    if (sqlType.startsWith("numeric")) return BigDecimal.class;
    if (sqlType.startsWith("timestamptz")) return Instant.class;
    return switch (sqlType) {
      case "date" -> LocalDate.class;
      case "boolean" -> required ? boolean.class : Boolean.class;
      case "bigint" -> required ? long.class : Long.class;
      case "integer" -> required ? int.class : Integer.class;
      case "smallint" -> required ? short.class : Short.class;
      case "bytea" -> byte[].class;
      default -> String.class;
    };
  }
}
