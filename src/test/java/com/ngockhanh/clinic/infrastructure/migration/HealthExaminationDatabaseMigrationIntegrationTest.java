package com.ngockhanh.clinic.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The database rules of the agreement, session and assignment model (migration V008) on a freshly
 * migrated PostgreSQL: roster identity, price lines, open session slots, live assignments, the
 * agreement scope of a session and the guard on reconciliation.
 */
@Testcontainers(disabledWithoutDocker = true)
class HealthExaminationDatabaseMigrationIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  private static JdbcTemplate jdbc;
  private static UUID account;
  private static UUID organization;
  private static UUID otherOrganization;

  @BeforeAll
  static void migrateAndSeedAnActor() {
    var flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();
    flyway.migrate();
    flyway.validate();
    assertThat(flyway.info().pending()).isEmpty();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    UUID staff =
        jdbc.queryForObject(
            "INSERT INTO public.staff_members(staff_code,full_name,status) VALUES ('TEST','Test Staff','ACTIVE') RETURNING id",
            UUID.class);
    account =
        jdbc.queryForObject(
            "INSERT INTO public.accounts(account_type,role_id,username,password_hash,staff_member_id,status) VALUES ('STAFF',(SELECT id FROM public.roles WHERE code = 'CLINIC_MANAGER'),'test-actor','TEST_INVALID_HASH',?,'ACTIVE') RETURNING id",
            UUID.class,
            staff);
    organization =
        insertOrganization("Test Organization", "test@example.invalid", "contact@example.invalid");
    otherOrganization =
        insertOrganization(
            "Other Organization", "other@example.invalid", "other-contact@example.invalid");
  }

  private static UUID insertOrganization(String name, String email, String contactEmail) {
    return jdbc.queryForObject(
        "INSERT INTO public.organizations(name,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'000',?,'Test Address','Test Contact','000',?,'ACTIVE') RETURNING id",
        UUID.class,
        name,
        email,
        contactEmail);
  }

  private static UUID insertAgreement(UUID org, String code) {
    return jdbc.queryForObject(
        "INSERT INTO public.health_examination_agreements(organization_id,agreement_code,name,status,created_by) VALUES (?,?,?,'DRAFT',?) RETURNING id",
        UUID.class,
        org,
        code,
        code,
        account);
  }

  private static UUID insertParticipant(UUID org, UUID agreement, String identification) {
    return jdbc.queryForObject(
        "INSERT INTO public.health_examination_participants(organization_id,agreement_id,full_name,date_of_birth,sex,identification_number,department_name,position_name) VALUES (?,?,'Test Participant',DATE '2000-01-01','MALE',?,'Test Department','Test Position') RETURNING id",
        UUID.class,
        org,
        agreement,
        identification);
  }

  private static UUID insertSession(
      UUID org, UUID agreement, String date, String activity, String siteType, String address) {
    return jdbc.queryForObject(
        "INSERT INTO public.health_examination_sessions(organization_id,agreement_id,session_date,activity,site_type,site_address,created_by) VALUES (?,?,CAST(? AS date),?,?,?,?) RETURNING id",
        UUID.class,
        org,
        agreement,
        date,
        activity,
        siteType,
        address,
        account);
  }

  private static UUID assign(UUID org, UUID participant, UUID session) {
    return jdbc.queryForObject(
        "INSERT INTO public.health_examination_session_assignments(organization_id,participant_id,session_id,recorded_by) VALUES (?,?,?,?) RETURNING id",
        UUID.class,
        org,
        participant,
        session,
        account);
  }

  private static String unique(String prefix) {
    return prefix + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  void aRosterIdentityIsUniquePerAgreementAndReconciliationNeedsServiceRows() {
    UUID agreement = insertAgreement(organization, unique("A"));
    UUID other = insertAgreement(organization, unique("A"));
    UUID participant = insertParticipant(organization, agreement, "000000000001");

    assertThatThrownBy(() -> insertParticipant(organization, agreement, "000000000001"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(insertParticipant(organization, other, "000000000001")).isNotEqualTo(participant);
    // a participant must carry the organization of its agreement
    assertThatThrownBy(() -> insertParticipant(otherOrganization, agreement, "000000000002"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT patient_id FROM public.health_examination_participants WHERE id=?",
                UUID.class,
                participant))
        .isNull();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.health_examination_participants SET service_reconciliation_status='RECONCILED' WHERE id=?",
                    participant))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.health_examination_agreements SET status='DELETED' WHERE id=?",
                    agreement))
        .isInstanceOf(DataIntegrityViolationException.class);
    // the roster has no attendance of its own any more
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name='health_examination_participants' AND column_name IN ('attendance_status','actual_examination_date','batch_day_id','legacy_day_id')",
                Integer.class))
        .isZero();
  }

  @Test
  void aPerformedServiceMustBelongToTheAgreementOfTheParticipant() {
    UUID agreement = insertAgreement(organization, unique("A"));
    UUID other = insertAgreement(organization, unique("A"));
    UUID participant = insertParticipant(organization, agreement, "000000000011");
    UUID department =
        jdbc.queryForObject(
            "INSERT INTO public.departments(code,name,department_type) VALUES (?,'Test Department','DIAGNOSTIC') RETURNING id",
            UUID.class,
            unique("D"));
    UUID service =
        jdbc.queryForObject(
            "INSERT INTO public.services(code,name,service_type,performing_department_id,unit_price) VALUES (?,'Test Service','LAB',?,100) RETURNING id",
            UUID.class,
            unique("S"),
            department);
    UUID wrongScope =
        jdbc.queryForObject(
            "INSERT INTO public.health_examination_agreement_services(agreement_id,service_id,reference_price_snapshot,negotiated_price,display_order) VALUES (?,?,100,80,1) RETURNING id",
            UUID.class,
            other,
            service);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.health_examination_participant_services(agreement_id,participant_id,agreement_service_id,is_performed,unit_price_snapshot,recorded_by) VALUES (?,?,?,true,80,?)",
                    agreement,
                    participant,
                    wrongScope,
                    account))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anAgreementOffersAServiceOnceWhileActiveButKeepsRetiredLines() {
    UUID agreement = insertAgreement(organization, unique("A"));
    UUID department =
        jdbc.queryForObject(
            "INSERT INTO public.departments(code,name,department_type) VALUES (?,'Test Department','DIAGNOSTIC') RETURNING id",
            UUID.class,
            unique("D"));
    UUID service =
        jdbc.queryForObject(
            "INSERT INTO public.services(code,name,service_type,performing_department_id,unit_price) VALUES (?,'Test Service','LAB',?,100) RETURNING id",
            UUID.class,
            unique("S"),
            department);
    String insert =
        "INSERT INTO public.health_examination_agreement_services(agreement_id,service_id,reference_price_snapshot,negotiated_price,display_order,active) VALUES (?,?,100,80,?,?)";

    jdbc.update(insert, agreement, service, 1, true);
    assertThatThrownBy(() -> jdbc.update(insert, agreement, service, 2, true))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update(insert, agreement, service, 3, false);
    jdbc.update(insert, agreement, service, 4, false);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_agreement_services WHERE agreement_id=?",
                Integer.class,
                agreement))
        .isEqualTo(3);
  }

  @Test
  void anOpenSessionSlotIsUniqueButACancelledOneFreesIt() {
    UUID agreement = insertAgreement(organization, unique("A"));
    UUID first = insertSession(organization, agreement, "2026-10-05", "ALL", "CLINIC", "");

    assertThatThrownBy(() -> insertSession(organization, null, "2026-10-05", "ALL", "CLINIC", ""))
        .isInstanceOf(DataIntegrityViolationException.class);
    // another activity, another date or another site is another slot
    insertSession(organization, null, "2026-10-05", "SAMPLING", "CLINIC", "");
    insertSession(organization, null, "2026-10-06", "ALL", "CLINIC", "");
    insertSession(organization, null, "2026-10-05", "ALL", "ORGANIZATION_SITE", "1 Test Street");
    // another organization has its own slots
    insertSession(otherOrganization, null, "2026-10-05", "ALL", "CLINIC", "");

    jdbc.update(
        "UPDATE public.health_examination_sessions SET status='CANCELLED' WHERE id=?", first);
    insertSession(organization, null, "2026-10-05", "ALL", "CLINIC", "");

    assertThatThrownBy(
            () -> insertSession(organization, null, "2026-10-07", "ALL", "ORGANIZATION_SITE", " "))
        .as("an organization site needs an address")
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertSession(organization, null, "2026-10-07", "NIGHT", "CLINIC", ""))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aParticipantIsInASessionOnceWhileLiveAndMayReturnAfterCancelling() {
    UUID agreement = insertAgreement(organization, unique("A"));
    UUID participant = insertParticipant(organization, agreement, "000000000021");
    UUID session = insertSession(organization, agreement, "2026-11-01", "ALL", "CLINIC", "");

    UUID assignment = assign(organization, participant, session);
    assertThatThrownBy(() -> assign(organization, participant, session))
        .isInstanceOf(DataIntegrityViolationException.class);

    jdbc.update(
        "UPDATE public.health_examination_session_assignments SET status='CANCELLED' WHERE id=?",
        assignment);
    assign(organization, participant, session);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_session_assignments WHERE participant_id=? AND session_id=?",
                Integer.class,
                participant,
                session))
        .isEqualTo(2);
  }

  @Test
  void anAttendedAssignmentNeedsATimeAndAMovedOneNeedsItsReplacement() {
    UUID agreement = insertAgreement(organization, unique("A"));
    UUID participant = insertParticipant(organization, agreement, "000000000031");
    UUID from = insertSession(organization, agreement, "2026-11-02", "ALL", "CLINIC", "");
    UUID to = insertSession(organization, agreement, "2026-11-03", "ALL", "CLINIC", "");
    UUID assignment = assign(organization, participant, from);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.health_examination_session_assignments SET status='ATTENDED' WHERE id=?",
                    assignment))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.health_examination_session_assignments SET attended_at=CURRENT_TIMESTAMP WHERE id=?",
                    assignment))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.health_examination_session_assignments SET status='MOVED' WHERE id=?",
                    assignment))
        .isInstanceOf(DataIntegrityViolationException.class);

    jdbc.update(
        "UPDATE public.health_examination_session_assignments SET status='ATTENDED',attended_at=CURRENT_TIMESTAMP WHERE id=?",
        assignment);
    UUID replacement = assign(organization, participant, to);
    UUID movedAway =
        assign(organization, insertParticipant(organization, agreement, "000000000032"), from);
    UUID movedTo =
        assign(
            organization,
            jdbc.queryForObject(
                "SELECT participant_id FROM public.health_examination_session_assignments WHERE id=?",
                UUID.class,
                movedAway),
            to);
    jdbc.update(
        "UPDATE public.health_examination_session_assignments SET status='MOVED',replaced_by_assignment_id=? WHERE id=?",
        movedTo,
        movedAway);

    assertThat(replacement).isNotNull();
  }

  @Test
  void aSessionOfAnotherAgreementOrOrganizationCannotTakeTheParticipant() {
    UUID agreement = insertAgreement(organization, unique("A"));
    UUID other = insertAgreement(organization, unique("A"));
    UUID participant = insertParticipant(organization, agreement, "000000000041");
    UUID ofOther = insertSession(organization, other, "2026-11-04", "ALL", "CLINIC", "");
    UUID shared = insertSession(organization, null, "2026-11-04", "SAMPLING", "CLINIC", "");
    UUID foreign = insertSession(otherOrganization, null, "2026-11-04", "ALL", "CLINIC", "");

    assertThatThrownBy(() -> assign(organization, participant, ofOther))
        .as("session reserved to another agreement")
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> assign(organization, participant, foreign))
        .as("session of another organization")
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(assign(organization, participant, shared)).as("a shared session").isNotNull();
  }

  @Test
  void permissionsUseTheAgreementVocabularyAndTheManagerHoldsTheNewOnes() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.permissions WHERE code LIKE 'HEALTH\\_EXAMINATION\\_BATCH\\_%' OR endpoint LIKE '%health-examination-batches%'",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT http_method || ' ' || endpoint FROM public.permissions WHERE code = 'HEALTH_EXAMINATION_AGREEMENT_ACTIVATE'",
                String.class))
        .isEqualTo("POST /api/v1/organizations/{organizationId}/agreements/{agreementId}/activate");
    assertThat(
            jdbc.queryForObject(
                "SELECT http_method || ' ' || endpoint FROM public.permissions WHERE code = 'HEALTH_EXAMINATION_AGREEMENT_END'",
                String.class))
        .isEqualTo("POST /api/v1/organizations/{organizationId}/agreements/{agreementId}/end");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.permissions WHERE code LIKE 'HEALTH\\_EXAMINATION\\_AGREEMENT\\_SERVICE\\_%'",
                Integer.class))
        .as("adding or retiring a service of an active agreement is gone")
        .isZero();
    for (String code :
        new String[] {
          "HEALTH_EXAMINATION_SESSION_VIEW",
          "HEALTH_EXAMINATION_SESSION_CREATE",
          "HEALTH_EXAMINATION_SESSION_UPDATE",
          "HEALTH_EXAMINATION_SESSION_CANCEL",
          "HEALTH_EXAMINATION_ASSIGNMENT_VIEW",
          "HEALTH_EXAMINATION_ASSIGNMENT_CREATE",
          "HEALTH_EXAMINATION_ASSIGNMENT_MOVE",
          "HEALTH_EXAMINATION_ASSIGNMENT_CANCEL",
          "HEALTH_EXAMINATION_ASSIGNMENT_ATTENDANCE_RECORD"
        })
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM public.role_permissions rp JOIN public.permissions p ON p.id=rp.permission_id JOIN public.roles r ON r.id=rp.role_id WHERE p.code=? AND r.code='CLINIC_MANAGER'",
                  Integer.class,
                  code))
          .as(code)
          .isEqualTo(1);
  }
}
