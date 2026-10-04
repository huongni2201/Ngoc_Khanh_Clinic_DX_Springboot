package com.ngockhanh.clinic.healthexamination.domain;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.*;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.*;
import com.ngockhanh.clinic.healthexamination.domain.entity.*;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.exception.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class HealthExaminationDomainTest {
  private HealthExaminationBatchParticipant participant() {
    return HealthExaminationBatchParticipant.create(
        id(10),
        id(1),
        id(3),
        new Roster(
            null,
            "Synthetic Person",
            LocalDate.of(1990, 1, 1),
            "MALE",
            IdentificationNumber.of("000000000001"),
            null,
            null,
            "Department",
            "Position"),
        null,
        null,
        NOW);
  }

  @Test
  void preparationAttendanceAndReconciliationAreIndependent() {
    var p = participant();
    assertThat(p.patientId()).isNull();
    assertThat(p.attendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);
    p.prepare(id(8), NOW);
    assertThat(p.attendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);
    p.recordAttendance(AttendanceStatus.ATTENDED, LocalDate.of(2026, 10, 4), id(6), NOW, null);
    p.reconcileServices(List.of(), scope(id(11)), id(6), NOW);
    assertThat(p.reconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    assertThat(p.services()).isEmpty();
    p.moveToDay(id(4));
    assertThat(p.patientId()).isEqualTo(id(8));
    assertThat(p.actualExaminationDate()).isEqualTo(LocalDate.of(2026, 10, 4));
  }

  @Test
  void rejectsInvalidAttendanceAndPatientRelinking() {
    var p = participant();
    assertThatThrownBy(() -> p.recordAttendance(AttendanceStatus.ATTENDED, null, id(6), NOW, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> p.recordAttendance(AttendanceStatus.ABSENT, LocalDate.now(), id(6), NOW, null))
        .isInstanceOf(IllegalArgumentException.class);
    p.prepare(id(8), NOW);
    p.prepare(id(8), NOW.plusSeconds(2));
    assertThat(p.preparedAt()).isEqualTo(NOW);
    assertThatThrownBy(() -> p.prepare(id(9), NOW)).isInstanceOf(DomainRuleViolation.class);
  }

  private HealthExaminationBatchParticipantService service() {
    return new HealthExaminationBatchParticipantService(
        id(12), id(1), id(10), id(11), true, null, Money.vnd("100"), id(6), NOW, NOW, NOW, 0);
  }

  @Test
  void reconciliationChecksBatchScopeAndRetainsRowsWhenUnchecked() {
    var p = participant();
    var s = service();
    assertThatThrownBy(() -> p.reconcileServices(List.of(s), List.of(), id(6), NOW))
        .isInstanceOf(ServiceOutsideBatchScope.class);
    p.reconcileServices(List.of(s), scope(id(11)), id(6), NOW);
    assertThatThrownBy(() -> p.reconcileServices(List.of(), scope(id(11)), id(6), NOW))
        .isInstanceOf(DomainRuleViolation.class);
    p.reconcileServices(List.of(s.recordPerformed(false, id(6), NOW)), scope(id(11)), id(6), NOW);
    assertThat(p.services().getFirst().performed()).isFalse();
    assertThat(p.services().getFirst().unitPriceSnapshot()).isEqualTo(Money.vnd("100"));
  }

  @Test
  void noInvalidOrUnassignedRowsCanCreateDurableImport() {
    var row = row(1);
    assertThatThrownBy(
            () ->
                new HealthExaminationImportJob(
                    id(5),
                    id(1),
                    id(6),
                    NOW,
                    List.of(id(3)),
                    List.of(row),
                    ImportStatus.VALIDATED,
                    null,
                    null,
                    null,
                    null,
                    0,
                    false,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    row.assignDay(id(3));
    row.reject("DUPLICATE_IN_BATCH");
    assertThatThrownBy(
            () ->
                new HealthExaminationImportJob(
                    id(5),
                    id(1),
                    id(6),
                    NOW,
                    List.of(id(3)),
                    List.of(row),
                    ImportStatus.VALIDATED,
                    null,
                    null,
                    null,
                    null,
                    0,
                    false,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void recordUsesStableMrnAndExplicitLifecycle() {
    var r = HealthExaminationRecord.prepare(id(20), id(10), id(21), "MRN-1", NOW);
    assertThatThrownBy(() -> r.issue(NOW)).isInstanceOf(DomainRuleViolation.class);
    r.start();
    r.complete(NOW);
    r.issue(NOW);
    assertThat(r.status()).isEqualTo(HealthExaminationRecordStatus.ISSUED);
    assertThat(r.mrn()).isEqualTo("MRN-1");
    assertThatThrownBy(() -> r.cancel(NOW, "Correction")).isInstanceOf(DomainRuleViolation.class);
  }

  @Test
  void reconciliationRejectsPriceIdentityOrRequestReplacement() {
    var p = participant();
    var original = service().linkServiceRequest(id(9));
    p.reconcileServices(List.of(original), scope(id(11)), id(6), NOW);
    for (int n = 0; n < 3; n++) {
      var replacement =
          new HealthExaminationBatchParticipantService(
              n == 0 ? id(22) : original.id(),
              original.batchId(),
              original.batchParticipantId(),
              original.batchServiceId(),
              true,
              n == 2 ? id(23) : original.serviceRequestId(),
              n == 1 ? Money.vnd("200") : original.unitPriceSnapshot(),
              id(6),
              NOW,
              NOW,
              NOW,
              0);
      assertThatThrownBy(() -> p.reconcileServices(List.of(replacement), scope(id(11)), id(6), NOW))
          .isInstanceOf(DomainRuleViolation.class);
    }
  }

  @Test
  void newServicesMustSnapshotCurrentNegotiatedPrice() {
    var p = participant();
    var wrong =
        new HealthExaminationBatchParticipantService(
            id(12), id(1), id(10), id(11), true, null, Money.vnd("200"), id(6), NOW, NOW, NOW, 0);
    assertThatThrownBy(() -> p.reconcileServices(List.of(wrong), scope(id(11)), id(6), NOW))
        .isInstanceOf(DomainRuleViolation.class);
  }
}
