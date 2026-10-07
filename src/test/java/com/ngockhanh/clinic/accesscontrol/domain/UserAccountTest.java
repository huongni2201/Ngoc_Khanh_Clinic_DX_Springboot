package com.ngockhanh.clinic.accesscontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.accesscontrol.domain.entity.UserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.enums.StaffMemberStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountTest {
  private static final UUID ID = UUID.randomUUID();
  private static final UUID OWNER = UUID.randomUUID();

  private static UserAccount staff(AccountStatus status, StaffMemberStatus staffStatus) {
    return new UserAccount(
        ID, AccountType.STAFF, "staff", "hash", status, OWNER, staffStatus, null, List.of());
  }

  @Test
  void activeStaffWithActiveStaffMemberCanSignInWithoutRoles() {
    assertThat(staff(AccountStatus.ACTIVE, StaffMemberStatus.ACTIVE).eligible()).isTrue();
  }

  @Test
  void lockedOrDisabledAccountsCannotSignIn() {
    assertThat(staff(AccountStatus.LOCKED, StaffMemberStatus.ACTIVE).eligible()).isFalse();
    assertThat(staff(AccountStatus.DISABLED, StaffMemberStatus.ACTIVE).eligible()).isFalse();
  }

  @Test
  void inactiveOrSuspendedStaffMemberCannotSignIn() {
    assertThat(staff(AccountStatus.ACTIVE, StaffMemberStatus.INACTIVE).eligible()).isFalse();
    assertThat(staff(AccountStatus.ACTIVE, StaffMemberStatus.SUSPENDED).eligible()).isFalse();
  }

  @Test
  void patientNeedsOnlyAPatientOwner() {
    assertThat(
            new UserAccount(
                    ID,
                    AccountType.PATIENT,
                    "p",
                    "hash",
                    AccountStatus.ACTIVE,
                    null,
                    null,
                    OWNER,
                    List.of())
                .eligible())
        .isTrue();
    assertThat(
            new UserAccount(
                    ID,
                    AccountType.PATIENT,
                    "p",
                    "hash",
                    AccountStatus.ACTIVE,
                    OWNER,
                    StaffMemberStatus.ACTIVE,
                    OWNER,
                    List.of())
                .eligible())
        .isFalse();
    assertThat(
            new UserAccount(
                    ID,
                    AccountType.PATIENT,
                    "p",
                    "hash",
                    AccountStatus.ACTIVE,
                    null,
                    null,
                    null,
                    List.of())
                .eligible())
        .isFalse();
  }

  @Test
  void unknownAccountTypeCannotSignIn() {
    assertThat(
            new UserAccount(
                ID,
                null,
                "x",
                "hash",
                AccountStatus.ACTIVE,
                OWNER,
                StaffMemberStatus.ACTIVE,
                null,
                List.of())
                .eligible())
        .isFalse();
  }
}
