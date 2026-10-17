package com.ngockhanh.clinic.healthexamination.domain;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Manual change rules of an agreement Participant: roster update, cancellation and reactivation.
 */
class HealthExaminationParticipantTest {
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

  private HealthExaminationParticipant participant() {
    return HealthExaminationParticipant.create(
        id(10), id(2), id(1), roster("000000000001", "Synthetic Person"), null, null, NOW);
  }

  private HealthExaminationParticipant restored(Progress progress) {
    return HealthExaminationParticipant.restore(
        id(10),
        id(2),
        id(1),
        roster("000000000001", "Synthetic Person"),
        progress,
        null,
        null,
        NOW,
        NOW,
        0,
        List.of());
  }

  private static Progress progress(RosterStatus roster, ReconciliationStatus reconciliation) {
    boolean reconciled = reconciliation == ReconciliationStatus.RECONCILED;
    return new Progress(
        null, roster, reconciliation, reconciled ? id(6) : null, reconciled ? NOW : null, null);
  }

  @Test
  void manualParticipantHasNoImportProvenance() {
    assertThat(participant().isManual()).isTrue();
  }

  @Test
  void aNewParticipantBelongsToAnAgreementAndHasNoSchedule() {
    var p = participant();
    assertThat(p.getAgreementId()).isEqualTo(id(1));
    assertThat(p.getOrganizationId()).isEqualTo(id(2));
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
  }

  @Test
  void updateRosterReplacesRosterAndKeepsProgress() {
    var p = participant();
    p.updateRoster(roster("000000000002", "Renamed Person"));
    assertThat(p.getRoster().fullName()).isEqualTo("Renamed Person");
    assertThat(p.getRoster().identificationNumber().value()).isEqualTo("000000000002");
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
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
  void cancelIsBlockedAfterReconciliation() {
    var reconciled = restored(progress(RosterStatus.ACTIVE, ReconciliationStatus.RECONCILED));
    assertThatThrownBy(reconciled::cancel).isInstanceOf(DomainRuleViolation.class);
    assertThat(reconciled.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
  }

  @Test
  void reactivateReturnsACancelledParticipantToActiveAndChangesNothingElse() {
    var p = participant();
    p.cancel();
    p.reactivate();
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(p.getRoster()).isEqualTo(roster("000000000001", "Synthetic Person"));
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
    assertThat(p.getPatientId()).isNull();
    assertThat(p.getPreparedAt()).isNull();
    assertThat(p.getImportJobId()).isNull();
    assertThat(p.getSourceRowNumber()).isNull();
  }

  @Test
  void reactivateKeepsImportProvenance() {
    var imported =
        HealthExaminationParticipant.restore(
            id(10),
            id(2),
            id(1),
            roster("000000000001", "Synthetic Person"),
            progress(RosterStatus.CANCELLED, ReconciliationStatus.PENDING),
            id(20),
            7,
            NOW,
            NOW,
            3,
            List.of());
    imported.reactivate();
    assertThat(imported.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(imported.getImportJobId()).isEqualTo(id(20));
    assertThat(imported.getSourceRowNumber()).isEqualTo(7);
    assertThat(imported.isManual()).isFalse();
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
  void reactivateIsBlockedForInconsistentPreparedOrReconciledRows() {
    var prepared =
        restored(
            new Progress(
                id(8), RosterStatus.CANCELLED, ReconciliationStatus.PENDING, null, null, NOW));
    var reconciled = restored(progress(RosterStatus.CANCELLED, ReconciliationStatus.RECONCILED));
    for (var p : List.of(prepared, reconciled)) {
      assertThatThrownBy(p::reactivate)
          .isInstanceOf(DomainRuleViolation.class)
          .hasMessage("Participant cannot be reactivated after preparation or attendance");
      assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    }
  }

  @Test
  void aReactivatedParticipantCanBeEditedAndCancelledAgain() {
    var p = participant();
    p.cancel();
    p.reactivate();
    p.updateRoster(roster("000000000001", "Renamed Person"));
    assertThat(p.getRoster().fullName()).isEqualTo("Renamed Person");
    p.cancel();
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
  }
}
