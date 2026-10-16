package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.AssignParticipantsCommand;
import com.ngockhanh.clinic.healthexamination.application.command.CancelAssignmentCommand;
import com.ngockhanh.clinic.healthexamination.application.command.CancelSessionCommand;
import com.ngockhanh.clinic.healthexamination.application.command.CreateSessionCommand;
import com.ngockhanh.clinic.healthexamination.application.command.MoveAssignmentCommand;
import com.ngockhanh.clinic.healthexamination.application.command.MoveSessionAssignmentsCommand;
import com.ngockhanh.clinic.healthexamination.application.command.RecordAttendanceCommand;
import com.ngockhanh.clinic.healthexamination.application.response.SessionResponse;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.usecase.AssignParticipantsToSessionUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CancelAssignmentUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CancelSessionUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateSessionUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetSessionUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.MoveAssignmentUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.MoveSessionAssignmentsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.RecordAssignmentAttendanceUseCase;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the session and assignment use cases against a real PostgreSQL database (Testcontainers;
 * skipped when Docker is unavailable): shared sessions, assigning and moving people, recording
 * attendance with the business-day rule, cancelling, and the audit written with each change.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SessionSchedulingIntegrationTest.SchedulingTestConfiguration.class)
class SessionSchedulingIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = {
        "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.audit.infrastructure.persistence.mapper"
      })
  @org.springframework.context.annotation.Import({
    SessionAccessPolicy.class,
    com.ngockhanh.clinic.healthexamination.application.service.AssignmentScopeChecks.class,
    CreateSessionUseCase.class,
    GetSessionUseCase.class,
    CancelSessionUseCase.class,
    AssignParticipantsToSessionUseCase.class,
    MoveAssignmentUseCase.class,
    MoveSessionAssignmentsUseCase.class,
    CancelAssignmentUseCase.class,
    RecordAssignmentAttendanceUseCase.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationAgreementRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisOrganizationRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationParticipantRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationSessionRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisSessionAssignmentRepository.class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class
  })
  static class SchedulingTestConfiguration {
    @Bean
    Clock clock() {
      // 2026-10-10 in Vietnam: sessions up to that day take attendance, later ones do not.
      return Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  private static final LocalDate PAST = LocalDate.of(2026, 10, 5);
  private static final LocalDate OTHER_PAST = LocalDate.of(2026, 10, 6);
  private static final LocalDate FUTURE = LocalDate.of(2026, 10, 20);

  @Autowired JdbcTemplate jdbc;
  @Autowired CreateSessionUseCase createSession;
  @Autowired GetSessionUseCase getSession;
  @Autowired CancelSessionUseCase cancelSession;
  @Autowired AssignParticipantsToSessionUseCase assign;
  @Autowired MoveAssignmentUseCase move;
  @Autowired MoveSessionAssignmentsUseCase moveAll;
  @Autowired CancelAssignmentUseCase cancelAssignment;
  @Autowired RecordAssignmentAttendanceUseCase attendance;

  UUID org, actor, agreementId, p1, p2, p3;
  UserPrincipal principal;

  @BeforeEach
  void fixture() {
    jdbc.execute(
        "TRUNCATE public.organizations,public.staff_members,public.services,public.departments CASCADE");
    org = UUID.randomUUID();
    actor = UUID.randomUUID();
    var staff = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.staff_members(id,staff_code,full_name,status) VALUES (?,'ACTOR','Synthetic Actor','ACTIVE')",
        staff);
    jdbc.update(
        "INSERT INTO public.accounts(id,account_type,role_id,username,password_hash,staff_member_id,status) VALUES (?,'STAFF',(SELECT id FROM public.roles WHERE code = 'CLINIC_MANAGER'),'actor','test-password-hash',?,'ACTIVE')",
        actor,
        staff);
    jdbc.update(
        "INSERT INTO public.organizations(id,name,tax_code,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'Synthetic Organization','0101234567','0901','o@example.test','Address','Contact','0902','c@example.test','ACTIVE')",
        org);
    agreementId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.health_examination_agreements(id,organization_id,agreement_code,name,status,created_by) VALUES (?,?,'KSK-1','Campaign','ACTIVE',?)",
        agreementId,
        org,
        actor);
    var department = UUID.randomUUID();
    var service = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.departments(id,code,name,department_type) VALUES (?,'D1','Exam','CLINICAL')",
        department);
    jdbc.update(
        "INSERT INTO public.services(id,code,name,service_type,performing_department_id,unit_price) VALUES (?,'S1','Exam','CONSULTATION',?,200)",
        service,
        department);
    jdbc.update(
        "INSERT INTO public.health_examination_agreement_services(agreement_id,service_id,reference_price_snapshot,negotiated_price,display_order,active) VALUES (?,?,200,100,1,true)",
        agreementId,
        service);
    p1 = participant("000000000001");
    p2 = participant("000000000002");
    p3 = participant("000000000003");
    principal =
        UserPrincipal.builder()
            .userId(actor)
            .staffId(staff)
            .username("actor")
            .principalType("STAFF")
            .roleAssignments(
                List.of(
                    new UserPrincipal.Assignment(
                        UUID.randomUUID(),
                        "CLINIC_MANAGER",
                        List.of(
                            SessionAccessPolicy.SESSION_VIEW,
                            SessionAccessPolicy.SESSION_DETAIL_VIEW,
                            SessionAccessPolicy.SESSION_CREATE,
                            SessionAccessPolicy.SESSION_UPDATE,
                            SessionAccessPolicy.SESSION_CANCEL,
                            SessionAccessPolicy.ASSIGNMENT_VIEW,
                            SessionAccessPolicy.ASSIGNMENT_CREATE,
                            SessionAccessPolicy.ASSIGNMENT_MOVE,
                            SessionAccessPolicy.ASSIGNMENT_MOVE_ALL,
                            SessionAccessPolicy.ASSIGNMENT_CANCEL,
                            SessionAccessPolicy.ASSIGNMENT_ATTENDANCE_RECORD))))
            .build();
  }

  private UUID participant(String identification) {
    return jdbc.queryForObject(
        "INSERT INTO public.health_examination_participants(organization_id,agreement_id,full_name,date_of_birth,sex,identification_number,department_name,position_name) VALUES (?,?,'Test Participant',DATE '2000-01-01','MALE',?,'Dept','Pos') RETURNING id",
        UUID.class,
        org,
        agreementId,
        identification);
  }

  private SessionResponse session(LocalDate date, String activity) {
    return createSession.execute(
        org,
        CreateSessionCommand.builder()
            .agreementId(agreementId)
            .sessionDate(date)
            .activity(activity)
            .siteType("CLINIC")
            .siteAddress("")
            .build(),
        principal);
  }

  private UUID assignmentOf(UUID participant, UUID session) {
    return jdbc.queryForObject(
        "SELECT id FROM public.health_examination_session_assignments WHERE participant_id=? AND session_id=? AND status IN ('ASSIGNED','ATTENDED','NO_SHOW')",
        UUID.class,
        participant,
        session);
  }

  private long assignmentVersion(UUID assignment) {
    return jdbc.queryForObject(
        "SELECT row_version FROM public.health_examination_session_assignments WHERE id=?",
        Long.class,
        assignment);
  }

  private String status(UUID assignment) {
    return jdbc.queryForObject(
        "SELECT status FROM public.health_examination_session_assignments WHERE id=?",
        String.class,
        assignment);
  }

  private int audits(String action) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM public.audit_events WHERE action=?", Integer.class, action);
  }

  @Test
  void aSlotHasOneOpenSessionAndItsCountsFollowTheAssignments() {
    var first = session(PAST, "ALL");

    assertThatThrownBy(() -> session(PAST, "ALL")).isInstanceOf(ConflictException.class);
    session(PAST, "SAMPLING");
    assertThat(first.status()).isEqualTo("OPEN");
    assertThat(first.assignedCount()).isZero();

    var result =
        assign.execute(org, first.id(), new AssignParticipantsCommand(List.of(p1, p2)), principal);

    assertThat(result.assigned()).hasSize(2);
    assertThat(result.skippedParticipantIds()).isEmpty();
    assertThat(getSession.execute(org, first.id(), principal).assignedCount()).isEqualTo(2);
    assertThat(audits("CREATE_HEALTH_EXAMINATION_SESSION")).isEqualTo(2);
  }

  @Test
  void assigningAParticipantAlreadyInTheSessionSkipsItInsteadOfFailing() {
    var s = session(PAST, "ALL");
    assign.execute(org, s.id(), new AssignParticipantsCommand(List.of(p1)), principal);

    var again =
        assign.execute(org, s.id(), new AssignParticipantsCommand(List.of(p1, p2)), principal);

    assertThat(again.skippedParticipantIds()).containsExactly(p1);
    assertThat(again.assigned()).extracting(a -> a.participantId()).containsExactly(p2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_session_assignments WHERE session_id=?",
                Integer.class,
                s.id()))
        .isEqualTo(2);
  }

  @Test
  void aCancelledParticipantCannotBeScheduled() {
    var s = session(PAST, "ALL");
    jdbc.update(
        "UPDATE public.health_examination_participants SET roster_status='CANCELLED' WHERE id=?",
        p3);

    assertThatThrownBy(
            () ->
                assign.execute(org, s.id(), new AssignParticipantsCommand(List.of(p3)), principal))
        .isInstanceOf(ConflictException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_session_assignments",
                Integer.class))
        .isZero();
  }

  @Test
  void movingKeepsTheHistoryAndRefusesAStaleVersionOrATargetAlreadyJoined() {
    var from = session(PAST, "ALL");
    var to = session(OTHER_PAST, "ALL");
    assign.execute(org, from.id(), new AssignParticipantsCommand(List.of(p1, p2)), principal);
    UUID a1 = assignmentOf(p1, from.id());
    long version = assignmentVersion(a1);

    assertThatThrownBy(
            () ->
                move.execute(
                    org, from.id(), a1, new MoveAssignmentCommand(to.id(), version + 5), principal))
        .isInstanceOf(ConcurrentUpdateException.class);

    var moved =
        move.execute(org, from.id(), a1, new MoveAssignmentCommand(to.id(), version), principal);

    // the answer is the new assignment; the old one stays as history pointing at it
    assertThat(moved.status()).isEqualTo("ASSIGNED");
    assertThat(moved.sessionId()).isEqualTo(to.id());
    assertThat(status(a1)).isEqualTo("MOVED");
    assertThat(
            jdbc.queryForObject(
                "SELECT replaced_by_assignment_id FROM public.health_examination_session_assignments WHERE id=?",
                UUID.class,
                a1))
        .isEqualTo(moved.id());
    assertThat(audits("MOVE_SESSION_ASSIGNMENT")).isEqualTo(1);

    // p2 joins the target on its own, then a move to the same target must be refused
    assign.execute(org, to.id(), new AssignParticipantsCommand(List.of(p2)), principal);
    UUID a2 = assignmentOf(p2, from.id());
    long v2 = assignmentVersion(a2);
    assertThatThrownBy(
            () ->
                move.execute(org, from.id(), a2, new MoveAssignmentCommand(to.id(), v2), principal))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void movingAWholeSessionMovesEveryExpectedParticipantAndSkipsThoseAlreadyThere() {
    var from = session(PAST, "ALL");
    var to = session(OTHER_PAST, "ALL");
    assign.execute(org, from.id(), new AssignParticipantsCommand(List.of(p1, p2, p3)), principal);
    assign.execute(org, to.id(), new AssignParticipantsCommand(List.of(p3)), principal);

    var result =
        moveAll.execute(org, from.id(), new MoveSessionAssignmentsCommand(to.id()), principal);

    assertThat(result.movedCount()).isEqualTo(2);
    assertThat(result.skippedParticipantIds()).containsExactly(p3);
    assertThat(getSession.execute(org, to.id(), principal).assignedCount()).isEqualTo(3);
  }

  @Test
  void attendanceFollowsTheBusinessDayAndCanBeCorrectedOnRetry() {
    var past = session(PAST, "ALL");
    var future = session(FUTURE, "ALL");
    assign.execute(org, past.id(), new AssignParticipantsCommand(List.of(p1, p2)), principal);
    assign.execute(org, future.id(), new AssignParticipantsCommand(List.of(p3)), principal);
    UUID a1 = assignmentOf(p1, past.id());
    UUID a2 = assignmentOf(p2, past.id());
    UUID a3 = assignmentOf(p3, future.id());

    var attended =
        attendance.execute(
            org,
            past.id(),
            a1,
            new RecordAttendanceCommand("ATTENDED", assignmentVersion(a1), null),
            principal);
    var noShow =
        attendance.execute(
            org,
            past.id(),
            a2,
            new RecordAttendanceCommand("NO_SHOW", assignmentVersion(a2), "did not come"),
            principal);

    assertThat(attended.status()).isEqualTo("ATTENDED");
    assertThat(attended.attendedAt()).isNotNull();
    assertThat(noShow.status()).isEqualTo("NO_SHOW");
    assertThat(getSession.execute(org, past.id(), principal))
        .satisfies(
            s -> {
              assertThat(s.attendedCount()).isEqualTo(1);
              assertThat(s.noShowCount()).isEqualTo(1);
            });
    assertThatThrownBy(
            () ->
                attendance.execute(
                    org,
                    future.id(),
                    a3,
                    new RecordAttendanceCommand("ATTENDED", assignmentVersion(a3), null),
                    principal))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("before the session date");

    // the no-show turns out to have come after all
    var corrected =
        attendance.execute(
            org,
            past.id(),
            a2,
            new RecordAttendanceCommand("ATTENDED", assignmentVersion(a2), null),
            principal);
    assertThat(corrected.status()).isEqualTo("ATTENDED");
    assertThat(audits("RECORD_SESSION_ATTENDANCE")).isEqualTo(3);
  }

  @Test
  void aSessionWithPeopleCannotBeCancelledUntilTheyAreMovedOrCancelled() {
    var s = session(PAST, "ALL");
    assign.execute(org, s.id(), new AssignParticipantsCommand(List.of(p1)), principal);
    UUID a1 = assignmentOf(p1, s.id());
    long sessionVersion = getSession.execute(org, s.id(), principal).rowVersion();

    assertThatThrownBy(
            () ->
                cancelSession.execute(
                    org, s.id(), new CancelSessionCommand(sessionVersion), principal))
        .isInstanceOf(ConflictException.class);

    var cancelled =
        cancelAssignment.execute(
            org,
            s.id(),
            a1,
            new CancelAssignmentCommand(assignmentVersion(a1), "left the company"),
            principal);
    assertThat(cancelled.status()).isEqualTo("CANCELLED");

    var closed =
        cancelSession.execute(org, s.id(), new CancelSessionCommand(sessionVersion), principal);
    assertThat(closed.status()).isEqualTo("CANCELLED");
    // the slot is free again
    assertThat(session(PAST, "ALL").status()).isEqualTo("OPEN");
  }

  @Test
  void aCallerWithoutThePermissionChangesNothing() {
    var s = session(PAST, "ALL");
    var nobody =
        UserPrincipal.builder()
            .userId(actor)
            .username("nobody")
            .principalType("STAFF")
            .roleAssignments(List.of())
            .build();

    assertThatThrownBy(
            () -> assign.execute(org, s.id(), new AssignParticipantsCommand(List.of(p1)), nobody))
        .isInstanceOf(ApplicationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_session_assignments",
                Integer.class))
        .isZero();
  }
}
