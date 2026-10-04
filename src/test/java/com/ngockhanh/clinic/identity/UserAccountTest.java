package com.ngockhanh.clinic.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.identity.domain.entity.UserAccount;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountTest {
  @Test
  void activeStaffCanAuthenticateWithoutRoleAssignments() {
    var account =
        new UserAccount(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "staff",
            "hash",
            "ACTIVE",
            "STAFF",
            "ACTIVE",
            List.of());

    assertThat(account.eligible()).isTrue();
  }

  @Test
  void patientNeedsPatientLinkButDoesNotNeedStaffOrRoles() {
    var account =
        new UserAccount(
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            "patient",
            "hash",
            "ACTIVE",
            "PATIENT",
            null,
            List.of());

    assertThat(account.eligible()).isTrue();
  }

  @Test
  void inactiveAccountsAndInconsistentIdentitiesCannotAuthenticate() {
    UUID id = UUID.randomUUID();
    assertThat(
            new UserAccount(id, id, null, "staff", "hash", "DISABLED", "STAFF", "ACTIVE", List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(id, id, null, "staff", "hash", "ACTIVE", "STAFF", null, List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(id, null, null, "patient", "hash", "ACTIVE", "PATIENT", null, List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(id, id, id, "patient", "hash", "ACTIVE", "PATIENT", "ACTIVE", List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(id, null, id, "patient", "hash", "LOCKED", "PATIENT", null, List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(id, id, null, "user", "hash", "ACTIVE", "UNKNOWN", "ACTIVE", List.of())
                .eligible())
        .isFalse();
  }

  @Test
  void lockedDisabledAccountsAndInactiveSuspendedStaffCannotAuthenticate() {
    UUID id = UUID.randomUUID();
    for (String status : List.of("LOCKED", "DISABLED")) {
      assertThat(
              new UserAccount(id, id, null, "staff", "hash", status, "STAFF", "ACTIVE", List.of())
                  .eligible())
          .isFalse();
    }
    for (String status : List.of("INACTIVE", "SUSPENDED")) {
      assertThat(
              new UserAccount(id, id, null, "staff", "hash", "ACTIVE", "STAFF", status, List.of())
                  .eligible())
          .isFalse();
    }
  }
}
