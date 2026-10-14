package com.ngockhanh.clinic.accesscontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffMember;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.Sex;
import com.ngockhanh.clinic.accesscontrol.domain.enums.StaffMemberStatus;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffAccountTest {
  private static final RoleRef RECEPTIONIST = new RoleRef(UUID.randomUUID(), "RECEPTIONIST");
  private static final RoleRef DOCTOR = new RoleRef(UUID.randomUUID(), "GENERAL_PRACTITIONER");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

  @Test
  void opensAnActiveAccountWithDistinctRolesOrderedByCode() {
    var account = open(List.of(RECEPTIONIST, DOCTOR, RECEPTIONIST));

    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.roleCodes()).containsExactly("GENERAL_PRACTITIONER", "RECEPTIONIST");
    assertThat(account.toString()).doesNotContain("{bcrypt}");
  }

  @Test
  void requiresARoleAndNeverGrantsThePatientRole() {
    assertThatThrownBy(() -> open(List.of())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> open(List.of(new RoleRef(UUID.randomUUID(), "PATIENT"))))
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

  private static StaffAccount open(List<RoleRef> roles) {
    return StaffAccount.open(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "annv1",
        "{bcrypt}hash",
        roles,
        UUID.randomUUID(),
        Instant.parse("2026-10-13T01:00:00Z"));
  }

  private static StaffAccount openWithUsername(String username) {
    return StaffAccount.open(
        UUID.randomUUID(),
        UUID.randomUUID(),
        username,
        "{bcrypt}hash",
        List.of(RECEPTIONIST),
        UUID.randomUUID(),
        Instant.parse("2026-10-13T01:00:00Z"));
  }
}
