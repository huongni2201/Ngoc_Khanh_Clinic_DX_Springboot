package com.ngockhanh.clinic.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class HealthExaminationDatabaseMigrationIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void batchRosterEnforcesDayScopeUniqueIdentityAndIndependentAttendance() {
    var flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    UUID staff =
        jdbc.queryForObject(
            "INSERT INTO public.staff_members(staff_code,full_name,status) VALUES ('TEST','Test Staff','ACTIVE') RETURNING id",
            UUID.class);
    UUID account =
        jdbc.queryForObject(
            "INSERT INTO public.accounts(account_type,username,password_hash,staff_member_id,status) VALUES ('STAFF','test-actor','TEST_INVALID_HASH',?,'ACTIVE') RETURNING id",
            UUID.class,
            staff);
    UUID organization =
        jdbc.queryForObject(
            "INSERT INTO public.organizations(code,name,organization_type,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES ('ORG','Test Organization','COMPANY','000','test@example.invalid','Test Address','Test Contact','000','contact@example.invalid','ACTIVE') RETURNING id",
            UUID.class);
    UUID batch = insertBatch(jdbc, organization, account, "B1");
    UUID otherBatch = insertBatch(jdbc, organization, account, "B2");
    UUID day =
        jdbc.queryForObject(
            "INSERT INTO public.health_examination_batch_days(batch_id,examination_date) VALUES (?,DATE '2026-10-05') RETURNING id",
            UUID.class,
            batch);
    UUID otherDay =
        jdbc.queryForObject(
            "INSERT INTO public.health_examination_batch_days(batch_id,examination_date) VALUES (?,DATE '2026-10-06') RETURNING id",
            UUID.class,
            otherBatch);
    assertThatThrownBy(() -> insertParticipant(jdbc, batch, otherDay, "000000000001"))
        .isInstanceOf(DataIntegrityViolationException.class);
    UUID participant = insertParticipant(jdbc, batch, day, "000000000001");
    assertThatThrownBy(() -> insertParticipant(jdbc, batch, day, "000000000001"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(insertParticipant(jdbc, otherBatch, otherDay, "000000000001"))
        .isNotEqualTo(participant);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.patients", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT patient_id FROM public.health_examination_batch_participants WHERE id=?",
                UUID.class,
                participant))
        .isNull();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.health_examination_batch_participants SET attendance_status='ATTENDED' WHERE id=?",
                    participant))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update(
        "UPDATE public.health_examination_batch_participants SET attendance_status='ATTENDED',actual_examination_date=DATE '2026-10-05',attendance_recorded_by=?,attendance_recorded_at=CURRENT_TIMESTAMP,row_version=row_version+1 WHERE id=? AND row_version=0",
        account,
        participant);
    assertThat(
            jdbc.queryForObject(
                "SELECT service_reconciliation_status FROM public.health_examination_batch_participants WHERE id=?",
                String.class,
                participant))
        .isEqualTo("PENDING");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.health_examination_batch_participants SET service_reconciliation_status='RECONCILED' WHERE id=?",
                    participant))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.health_examination_batches SET status='DELETED' WHERE id=?",
                    batch))
        .isInstanceOf(DataIntegrityViolationException.class);

    UUID department =
        jdbc.queryForObject(
            "INSERT INTO public.departments(code,name,department_type) VALUES ('D','Test Department','DIAGNOSTIC') RETURNING id",
            UUID.class);
    UUID service =
        jdbc.queryForObject(
            "INSERT INTO public.services(code,name,service_type,performing_department_id,unit_price) VALUES ('S','Test Service','LAB',?,100) RETURNING id",
            UUID.class,
            department);
    UUID wrongScope =
        jdbc.queryForObject(
            "INSERT INTO public.health_examination_batch_services(batch_id,service_id,reference_price_snapshot,negotiated_price,display_order) VALUES (?,?,100,80,1) RETURNING id",
            UUID.class,
            otherBatch,
            service);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.health_examination_participant_services(batch_id,batch_participant_id,batch_service_id,is_performed,unit_price_snapshot,recorded_by) VALUES (?,?,?,true,80,?)",
                    batch,
                    participant,
                    wrongScope,
                    account))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static UUID insertBatch(JdbcTemplate jdbc, UUID organization, UUID actor, String code) {
    return jdbc.queryForObject(
        "INSERT INTO public.health_examination_batches(organization_id,batch_code,name,examination_site_type,examination_site_name,examination_site_address,status,created_by) VALUES (?,?,?,'CLINIC','Test Site','Test Address','DRAFT',?) RETURNING id",
        UUID.class,
        organization,
        code,
        code,
        actor);
  }

  private static UUID insertParticipant(
      JdbcTemplate jdbc, UUID batch, UUID day, String identification) {
    return jdbc.queryForObject(
        "INSERT INTO public.health_examination_batch_participants(batch_id,batch_day_id,full_name,date_of_birth,sex,identification_number,department_name,position_name) VALUES (?,?,'Test Participant',DATE '2000-01-01','MALE',?,'Test Department','Test Position') RETURNING id",
        UUID.class,
        batch,
        day,
        identification);
  }
}
