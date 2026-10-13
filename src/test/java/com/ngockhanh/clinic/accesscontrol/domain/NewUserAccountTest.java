package com.ngockhanh.clinic.accesscontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.domain.entity.NewUserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AssignableRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NewUserAccountTest {
  private static final UUID ID = UUID.randomUUID();
  private static final UUID OWNER = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
  private static final String HASH = "synthetic-password-hash";
  private static final AssignableRole DOCTOR =
      new AssignableRole(
          UUID.randomUUID(), "GENERAL_PRACTITIONER", "General Practitioner", "", true);
  private static final AssignableRole NURSE =
      new AssignableRole(UUID.randomUUID(), "NURSE", "Nurse", "", true);
  private static final AssignableRole PATIENT =
      new AssignableRole(UUID.randomUUID(), "PATIENT", "Patient", "", true);

  @Test
  void staffAccountIsActiveAndLinkedToTheStaffMemberWithItsRoles() {
    NewUserAccount account =
        NewUserAccount.forStaff(ID, "nguyen.van.a", HASH, OWNER, List.of(DOCTOR, NURSE), NOW);

    assertThat(account.type()).isEqualTo(AccountType.STAFF);
    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.staffMemberId()).isEqualTo(OWNER);
    assertThat(account.patientId()).isNull();
    assertThat(account.ownerId()).isEqualTo(OWNER);
    assertThat(account.roleCodes()).containsExactly("GENERAL_PRACTITIONER", "NURSE");
  }

  @Test
  void patientAccountHoldsOnlyThePatientRole() {
    NewUserAccount account = NewUserAccount.forPatient(ID, "le.thi.hoa", HASH, OWNER, PATIENT, NOW);

    assertThat(account.type()).isEqualTo(AccountType.PATIENT);
    assertThat(account.patientId()).isEqualTo(OWNER);
    assertThat(account.staffMemberId()).isNull();
    assertThat(account.roleCodes()).containsExactly("PATIENT");
  }

  @Test
  void staffAccountNeedsAtLeastOneStaffRole() {
    assertThatThrownBy(() -> NewUserAccount.forStaff(ID, "user", HASH, OWNER, List.of(), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> NewUserAccount.forStaff(ID, "user", HASH, OWNER, List.of(DOCTOR, PATIENT), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void patientAccountRejectsAMissingOrStaffRole() {
    assertThatThrownBy(() -> NewUserAccount.forPatient(ID, "user", HASH, OWNER, null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NewUserAccount.forPatient(ID, "user", HASH, OWNER, DOCTOR, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void inactiveOrDuplicateRolesAreRejected() {
    AssignableRole inactive =
        new AssignableRole(UUID.randomUUID(), "RECEPTIONIST", "Receptionist", "", false);
    assertThatThrownBy(
            () -> NewUserAccount.forStaff(ID, "user", HASH, OWNER, List.of(inactive), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> NewUserAccount.forStaff(ID, "user", HASH, OWNER, List.of(DOCTOR, DOCTOR), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void usernameMustNotHaveSurroundingSpaces() {
    assertThatThrownBy(
            () -> NewUserAccount.forStaff(ID, " user", HASH, OWNER, List.of(DOCTOR), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NewUserAccount.forStaff(ID, " ", HASH, OWNER, List.of(DOCTOR), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toStringHidesTheUsernameAndPasswordHash() {
    NewUserAccount account =
        NewUserAccount.forStaff(ID, "nguyen.van.a", HASH, OWNER, List.of(DOCTOR), NOW);

    assertThat(account.toString()).doesNotContain("nguyen.van.a").doesNotContain(HASH);
  }
}
