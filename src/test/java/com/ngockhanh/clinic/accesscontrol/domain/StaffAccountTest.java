package com.ngockhanh.clinic.accesscontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffMember;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.Sex;
import com.ngockhanh.clinic.accesscontrol.domain.enums.StaffMemberStatus;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffAccountTest {
  private static final RoleRef RECEPTIONIST = new RoleRef(UUID.randomUUID(), "RECEPTIONIST");
  private static final RoleRef DOCTOR = new RoleRef(UUID.randomUUID(), "GENERAL_PRACTITIONER");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

  @Test
  void opensAnActiveAccountHoldingOneRole() {
    var account = open(DOCTOR);

    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.role()).isEqualTo(DOCTOR);
    assertThat(account.toString()).doesNotContain("{bcrypt}");
  }

  @Test
  void requiresARoleAndNeverHoldsThePatientRole() {
    assertThatThrownBy(() -> open(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> open(new RoleRef(UUID.randomUUID(), "PATIENT")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAnUntrimmedOrOverlongUsername() {
    assertThatThrownBy(() -> openWithUsername(" annv1")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> openWithUsername("a".repeat(151)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createsAnActiveStaffMemberWithTrimmedOptionalDetails() {
    var staff =
        StaffMember.create(
            UUID.randomUUID(), "NV-0001", "  Nguyễn Văn An ", null, Sex.MALE, " ", " a@b.vn ", TODAY);

    assertThat(staff.status()).isEqualTo(StaffMemberStatus.ACTIVE);
    assertThat(staff.fullName()).isEqualTo("Nguyễn Văn An");
    assertThat(staff.phone()).isNull();
    assertThat(staff.email()).isEqualTo("a@b.vn");
    assertThat(staff.rowVersion()).isZero();
  }

  @Test
  void rejectsANameWithoutLettersAndABirthDateThatIsNotPast() {
    assertThatThrownBy(
            () -> StaffMember.create(UUID.randomUUID(), "NV-0001", "123", null, null, null, null, TODAY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                StaffMember.create(
                    UUID.randomUUID(), "NV-0001", "An", TODAY, null, null, null, TODAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static StaffAccount open(RoleRef role) {
    return StaffAccount.open(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "annv1",
        "{bcrypt}hash",
        role);
  }

  private static StaffAccount openWithUsername(String username) {
    return StaffAccount.open(
        UUID.randomUUID(),
        UUID.randomUUID(),
        username,
        "{bcrypt}hash",
        RECEPTIONIST);
  }
}
