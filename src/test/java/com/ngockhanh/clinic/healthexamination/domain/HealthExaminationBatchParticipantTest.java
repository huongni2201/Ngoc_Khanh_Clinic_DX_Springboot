package com.ngockhanh.clinic.healthexamination.domain;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Manual change rules of a batch Participant: roster update, cancellation and reactivation. */
class HealthExaminationBatchParticipantTest {
  private static Roster roster(String identification, String name) {
    return new Roster(
        "NV-001",
        name,
        LocalDate.of(1990, 1, 1),
        "MALE",
        IdentificationNumber.of(identification),
        null,
        null,
        "Department",
        "Position");
  }

  private HealthExaminationBatchParticipant participant() {
    return HealthExaminationBatchParticipant.create(
        id(10), id(1), id(3), roster("000000000001", "Synthetic Person"), null, null, NOW);
  }

  private HealthExaminationBatchParticipant restored(Progress progress) {
    return HealthExaminationBatchParticipant.restore(
        id(10),
        id(1),
        id(3),
        roster("000000000001", "Synthetic Person"),
        progress,
        null,
        null,
        NOW,
        NOW,
        0,
        List.of());
  }

  private static Progress progress(
      RosterStatus roster, AttendanceStatus attendance, ReconciliationStatus reconciliation) {
    boolean attended = attendance == AttendanceStatus.ATTENDED;
    boolean reconciled = reconciliation == ReconciliationStatus.RECONCILED;
    return new Progress(
        null,
        roster,
        attendance,
        attended ? LocalDate.of(2026, 10, 4) : null,
        attended ? id(6) : null,
        attended ? NOW : null,
        null,
        reconciliation,
        reconciled ? id(6) : null,
        reconciled ? NOW : null,
        null);
  }

  @Test
  void manualParticipantHasNoImportProvenance() {
    assertThat(participant().isManual()).isTrue();
  }

  @Test
  void updateRosterReplacesRosterAndKeepsProgress() {
    var p = participant();
    p.updateRoster(roster("000000000002", "Renamed Person"));
    assertThat(p.getRoster().fullName()).isEqualTo("Renamed Person");
    assertThat(p.getRoster().identificationNumber().value()).isEqualTo("000000000002");
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
    assertThat(p.getPatientId()).isNull();
  }

  @Test
  void identificationNumberIsLockedOnceLinkedToAPatient() {
    var p = participant();
    p.prepare(id(8), NOW);
    assertThatThrownBy(() -> p.updateRoster(roster("000000000002", "Synthetic Person")))
        .isInstanceOf(DomainRuleViolation.class)
        .hasMessage("Identification number is locked after visit preparation");
    // Other fields stay editable and the same identification number is accepted.
    p.updateRoster(roster("000000000001", "Renamed Person"));
    assertThat(p.getRoster().fullName()).isEqualTo("Renamed Person");
    assertThat(p.getRoster().identificationNumber().value()).isEqualTo("000000000001");
  }

  @Test
  void updateRosterRejectsMissingRoster() {
    assertThatThrownBy(() -> participant().updateRoster(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cancelledParticipantCannotBeUpdatedOrCancelledAgain() {
    var p = participant();
    p.cancel();
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    assertThatThrownBy(() -> p.updateRoster(roster("000000000001", "Other Name")))
        .isInstanceOf(DomainRuleViolation.class)
        .hasMessage("Participant is cancelled");
    assertThatThrownBy(p::cancel)
        .isInstanceOf(DomainRuleViolation.class)
        .hasMessage("Participant is cancelled");
  }

  @Test
  void cancelChangesOnlyTheRosterStatus() {
    var p = participant();
    p.cancel();
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    assertThat(p.getRoster()).isEqualTo(roster("000000000001", "Synthetic Person"));
    assertThat(p.getBatchDayId()).isEqualTo(id(3));
    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
    assertThat(p.getPatientId()).isNull();
    assertThat(p.getPreparedAt()).isNull();
    assertThat(p.getRowVersion()).isZero();
  }

  @Test
  void cancelIsBlockedAfterPreparation() {
    var p = participant();
    p.prepare(id(8), NOW);
    assertThatThrownBy(p::cancel)
        .isInstanceOf(DomainRuleViolation.class)
        .hasMessage("Participant cannot be cancelled after preparation or attendance");
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
  }

  @Test
  void cancelIsBlockedAfterAttendanceOrReconciliation() {
    var attended =
        restored(
            progress(RosterStatus.ACTIVE, AttendanceStatus.ATTENDED, ReconciliationStatus.PENDING));
    assertThatThrownBy(attended::cancel).isInstanceOf(DomainRuleViolation.class);
    assertThat(attended.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);

    var reconciled =
        restored(
            progress(
                RosterStatus.ACTIVE, AttendanceStatus.ABSENT, ReconciliationStatus.RECONCILED));
    assertThatThrownBy(reconciled::cancel).isInstanceOf(DomainRuleViolation.class);
    assertThat(reconciled.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
  }

  @Test
  void absentParticipantThatWasNeverPreparedCanBeCancelled() {
    var p =
        restored(
            progress(RosterStatus.ACTIVE, AttendanceStatus.ABSENT, ReconciliationStatus.PENDING));
    p.cancel();
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
  }

  @Test
  void reactivateReturnsACancelledParticipantToActiveAndChangesNothingElse() {
    var p = participant();
    p.cancel();
    p.reactivate();
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(p.getRoster()).isEqualTo(roster("000000000001", "Synthetic Person"));
    assertThat(p.getBatchDayId()).isEqualTo(id(3));
    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
    assertThat(p.getPatientId()).isNull();
    assertThat(p.getPreparedAt()).isNull();
    assertThat(p.getImportJobId()).isNull();
    assertThat(p.getSourceRowNumber()).isNull();
  }

  @Test
  void reactivateKeepsAttendanceNoteAndImportProvenance() {
    var absent =
        HealthExaminationBatchParticipant.restore(
            id(10),
            id(1),
            id(3),
            roster("000000000001", "Synthetic Person"),
            new Progress(
                null,
                RosterStatus.CANCELLED,
                AttendanceStatus.ABSENT,
                null,
                id(6),
                NOW,
                "Called in sick",
                ReconciliationStatus.PENDING,
                null,
                null,
                null),
            id(20),
            7,
            NOW,
            NOW,
            3,
            List.of());
    absent.reactivate();
    assertThat(absent.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(absent.getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
    assertThat(absent.getAttendanceNote()).isEqualTo("Called in sick");
    assertThat(absent.getImportJobId()).isEqualTo(id(20));
    assertThat(absent.getSourceRowNumber()).isEqualTo(7);
    assertThat(absent.isManual()).isFalse();
  }

  @Test
  void reactivateRejectsAnActiveParticipant() {
    var p = participant();
    assertThatThrownBy(p::reactivate)
        .isInstanceOf(DomainRuleViolation.class)
        .hasMessage("Participant is not cancelled");
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
  }

  @Test
  void reactivateIsBlockedForInconsistentPreparedAttendedOrReconciledRows() {
    var prepared =
        restored(
            new Progress(
                id(8),
                RosterStatus.CANCELLED,
                AttendanceStatus.UNCONFIRMED,
                null,
                null,
                null,
                null,
                ReconciliationStatus.PENDING,
                null,
                null,
                NOW));
    var attended =
        restored(
            progress(
                RosterStatus.CANCELLED, AttendanceStatus.ATTENDED, ReconciliationStatus.PENDING));
    var reconciled =
        restored(
            progress(
                RosterStatus.CANCELLED, AttendanceStatus.ABSENT, ReconciliationStatus.RECONCILED));
    for (var p : List.of(prepared, attended, reconciled)) {
      assertThatThrownBy(p::reactivate)
          .isInstanceOf(DomainRuleViolation.class)
          .hasMessage("Participant cannot be reactivated after preparation or attendance");
      assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    }
  }

  @Test
  void aReactivatedParticipantCanBeMovedEditedAndCancelledAgain() {
    var p = participant();
    p.cancel();
    assertThatThrownBy(() -> p.moveToDay(id(4)))
        .isInstanceOf(DomainRuleViolation.class)
        .hasMessage("Participant is cancelled");
    p.reactivate();
    p.moveToDay(id(4));
    p.updateRoster(roster("000000000001", "Renamed Person"));
    assertThat(p.getBatchDayId()).isEqualTo(id(4));
    assertThat(p.getRoster().fullName()).isEqualTo("Renamed Person");
    p.cancel();
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
  }
}
