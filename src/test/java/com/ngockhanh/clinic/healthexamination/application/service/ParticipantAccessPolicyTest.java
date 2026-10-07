package com.ngockhanh.clinic.healthexamination.application.service;

import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.staff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ParticipantAccessPolicyTest {
  private final ParticipantAccessPolicy policy = new ParticipantAccessPolicy();

  private record Operation(String permission, Consumer<UserPrincipal> authorize) {}

  @Test
  void eachOperationRequiresItsOwnPermissionAndAStaffAccount() {
    var operations =
        List.of(
            new Operation("PARTICIPANT_VIEW", policy::requireRead),
            new Operation("PARTICIPANT_TEMPLATE_DOWNLOAD", policy::requireTemplateDownload),
            new Operation("PARTICIPANT_IMPORT", policy::requireImport),
            new Operation("PARTICIPANT_CREATE", policy::requireCreate),
            new Operation("PARTICIPANT_UPDATE", policy::requireUpdate),
            new Operation("PARTICIPANT_REMOVE", policy::requireRemove),
            new Operation("PARTICIPANT_REACTIVATE", policy::requireReactivate));
    for (var operation : operations) {
      assertThatCode(() -> operation.authorize().accept(staff(operation.permission())))
          .as(operation.permission())
          .doesNotThrowAnyException();
      for (var other : operations) {
        if (!operation.permission().equals(other.permission()))
          assertDenied(operation, staff(other.permission()));
      }
      assertDenied(operation, null);
      assertDenied(operation, staff());
      assertDenied(operation, staff("HEALTH_EXAMINATION_PARTICIPANT_MANAGE"));
      assertDenied(
          operation,
          UserPrincipal.builder()
              .userId(UUID.randomUUID())
              .patientId(UUID.randomUUID())
              .principalType("PATIENT")
              .roleAssignments(staff(operation.permission()).roleAssignments())
              .build());
      assertDenied(
          operation,
          UserPrincipal.builder()
              .principalType("STAFF")
              .roleAssignments(staff(operation.permission()).roleAssignments())
              .build());
    }
  }

  private static void assertDenied(Operation operation, UserPrincipal principal) {
    assertThatThrownBy(() -> operation.authorize().accept(principal))
        .as(operation.permission())
        .isInstanceOfSatisfying(
            ApplicationException.class,
            failure ->
                assertThat(failure.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
  }
}
