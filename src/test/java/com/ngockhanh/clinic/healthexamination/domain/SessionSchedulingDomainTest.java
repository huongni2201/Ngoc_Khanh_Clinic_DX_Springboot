package com.ngockhanh.clinic.healthexamination.domain;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.SessionAssignment;
import com.ngockhanh.clinic.healthexamination.domain.enums.AssignmentStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Rules of the session and of the assignment of a Participant to a session. */
class SessionSchedulingDomainTest {
  private static final ExaminationSite CLINIC =
      new ExaminationSite(ExaminationSiteType.CLINIC, null);
  private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

  private HealthExaminationSession session(Object agreement) {
    return HealthExaminationSession.open(
        id(30), id(2), agreement == null ? null : id(1), DAY, SessionActivity.ALL, CLINIC);
  }

  private SessionAssignment assigned() {
    return SessionAssignment.assign(id(40), id(2), id(10), id(30), id(6), NOW);
  }

  @Test
  void aNewSessionIsOpenAndHasNoCapacityOrParticipantState() {
    var s = session(null);
    assertThat(s.status()).isEqualTo(SessionStatus.OPEN);
    assertThat(s.agreementId()).isNull();
    assertThat(s.site().address()).isEmpty();
    assertThat(s.rowVersion()).isZero();
  }

  @Test
  void anOrganizationSiteNeedsAnAddress() {
    assertThatThrownBy(() -> new ExaminationSite(ExaminationSiteType.ORGANIZATION_SITE, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aSessionCanBeRescheduledWhileOpenAndNotAfterItIsCancelled() {
    var s = session(null);
    var site = new ExaminationSite(ExaminationSiteType.ORGANIZATION_SITE, "12 Le Loi");

    s.reschedule(DAY.plusDays(2), SessionActivity.SAMPLING, site);

    assertThat(s.sessionDate()).isEqualTo(DAY.plusDays(2));
    assertThat(s.activity()).isEqualTo(SessionActivity.SAMPLING);
    assertThat(s.site()).isEqualTo(site);
    s.cancel();
    assertThat(s.status()).isEqualTo(SessionStatus.CANCELLED);
    assertThatThrownBy(() -> s.reschedule(DAY, SessionActivity.ALL, CLINIC))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(s::cancel).isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(s::requireOpen).isInstanceOf(DomainRuleViolation.class);
  }

  @Test
  void aSessionOfOneAgreementServesOnlyThatAgreement() {
    var scoped = session("agreement");
    scoped.requireServes(id(1));
    assertThatThrownBy(() -> scoped.requireServes(id(5))).isInstanceOf(DomainRuleViolation.class);
    session(null).requireServes(id(5));
  }

  @Test
  void anAssignmentStartsExpected() {
    var a = assigned();
    assertThat(a.status()).isEqualTo(AssignmentStatus.ASSIGNED);
    assertThat(a.attendedAt()).isNull();
    assertThat(a.replacedByAssignmentId()).isNull();
    assertThat(a.status().isLive()).isTrue();
  }

  @Test
  void attendanceIsRecordedAndCanBeCorrected() {
    var a = assigned();
    assertThat(a.recordAttendance(AssignmentStatus.ATTENDED, null, id(6), NOW.plusSeconds(60)))
        .isTrue();
    assertThat(a.status()).isEqualTo(AssignmentStatus.ATTENDED);
    assertThat(a.attendedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(a.recordAttendance(AssignmentStatus.ATTENDED, null, id(6), NOW.plusSeconds(120)))
        .as("the same state writes nothing")
        .isFalse();
    assertThat(a.attendedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(
            a.recordAttendance(
                AssignmentStatus.NO_SHOW, "wrong click", id(6), NOW.plusSeconds(180)))
        .isTrue();
    assertThat(a.attendedAt()).isNull();
    assertThat(a.status().isLive()).isFalse();
    assertThat(a.recordAttendance(AssignmentStatus.ASSIGNED, null, id(6), NOW.plusSeconds(240)))
        .isTrue();
    assertThat(a.status()).isEqualTo(AssignmentStatus.ASSIGNED);
  }

  @Test
  void onlyAttendanceStatesCanBeRecorded() {
    var a = assigned();
    assertThatThrownBy(() -> a.recordAttendance(AssignmentStatus.MOVED, null, id(6), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> a.recordAttendance(AssignmentStatus.CANCELLED, null, id(6), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anExpectedAssignmentCanBeMovedAndKeepsTheLinkToItsReplacement() {
    var a = assigned();
    a.moveTo(id(41), id(6), NOW);
    assertThat(a.status()).isEqualTo(AssignmentStatus.MOVED);
    assertThat(a.replacedByAssignmentId()).isEqualTo(id(41));
    assertThatThrownBy(() -> a.recordAttendance(AssignmentStatus.ATTENDED, null, id(6), NOW))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> a.moveTo(id(42), id(6), NOW)).isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> a.cancel(null, id(6), NOW)).isInstanceOf(DomainRuleViolation.class);
  }

  @Test
  void anAttendedAssignmentIsAFactThatCannotBeMovedOrCancelled() {
    var a = assigned();
    a.recordAttendance(AssignmentStatus.ATTENDED, null, id(6), NOW);
    assertThatThrownBy(() -> a.moveTo(id(41), id(6), NOW)).isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> a.cancel("no", id(6), NOW)).isInstanceOf(DomainRuleViolation.class);
    assertThat(a.status()).isEqualTo(AssignmentStatus.ATTENDED);
  }

  @Test
  void aNoShowStaysAsHistoryAndIsNotMovedOrCancelled() {
    var a = assigned();
    a.recordAttendance(AssignmentStatus.NO_SHOW, null, id(6), NOW);
    assertThatThrownBy(() -> a.moveTo(id(41), id(6), NOW)).isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> a.cancel(null, id(6), NOW)).isInstanceOf(DomainRuleViolation.class);
  }

  @Test
  void anExpectedAssignmentCanBeCancelledWithAReason() {
    var a = assigned();
    a.cancel("company postponed", id(6), NOW);
    assertThat(a.status()).isEqualTo(AssignmentStatus.CANCELLED);
    assertThat(a.note()).isEqualTo("company postponed");
  }

  @Test
  void restoreRejectsAnAttendedAssignmentWithoutItsTimeAndAMovedOneWithoutItsReplacement() {
    assertThatThrownBy(
            () ->
                SessionAssignment.restore(
                    id(40),
                    id(2),
                    id(10),
                    id(30),
                    AssignmentStatus.ATTENDED,
                    null,
                    null,
                    null,
                    id(6),
                    NOW,
                    NOW,
                    NOW,
                    0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                SessionAssignment.restore(
                    id(40),
                    id(2),
                    id(10),
                    id(30),
                    AssignmentStatus.MOVED,
                    null,
                    null,
                    null,
                    id(6),
                    NOW,
                    NOW,
                    NOW,
                    0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
