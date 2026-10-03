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
            true,
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
            false,
            List.of());

    assertThat(account.eligible()).isTrue();
  }

  @Test
  void inactiveAccountsAndInconsistentIdentitiesCannotAuthenticate() {
    UUID id = UUID.randomUUID();
    assertThat(
            new UserAccount(id, id, null, "staff", "hash", "INACTIVE", "STAFF", true, List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(id, id, null, "staff", "hash", "ACTIVE", "STAFF", false, List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(
                    id, null, null, "patient", "hash", "ACTIVE", "PATIENT", false, List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(id, id, id, "patient", "hash", "ACTIVE", "PATIENT", true, List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(
                    id, null, id, "patient", "hash", "INACTIVE", "PATIENT", false, List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(id, id, null, "user", "hash", "ACTIVE", "UNKNOWN", true, List.of())
                .eligible())
        .isFalse();
  }
}
