package com.ngockhanh.clinic.accesscontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.exception.ProtectedRoleException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleTest {
  @Test
  void createsWithTrimmedDetailsAndBlankDescriptionAsNull() {
    Role role = Role.create(UUID.randomUUID(), "LAB_TECH_2", "  Lab technician ", "   ", true);

    assertThat(role.name()).isEqualTo("Lab technician");
    assertThat(role.description()).isNull();
    assertThat(role.active()).isTrue();
    assertThat(role.rowVersion()).isZero();
  }

  @Test
  void rejectsCodesOutsideTheSrsFormat() {
    for (String code : List.of("nurse", "1NURSE", "_NURSE", "NURSE-2", "NỤRSE", "", "A".repeat(81)))
      assertThatThrownBy(() -> Role.create(UUID.randomUUID(), code, "Name", null, true))
          .as(code)
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMissingOrTooLongDetails() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> Role.create(id, "NURSE", " ", null, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Role.create(id, "NURSE", "N".repeat(101), null, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Role.create(id, "NURSE", "Nurse", "d".repeat(501), true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Role.create(id, "NURSE", "Nurse", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updatesEverythingButTheCode() {
    Role role = Role.restore(UUID.randomUUID(), "NURSE", "Nurse", "Old", true, 3L);

    role.update("Head nurse", "New", false);

    assertThat(role.code()).isEqualTo("NURSE");
    assertThat(role.name()).isEqualTo("Head nurse");
    assertThat(role.description()).isEqualTo("New");
    assertThat(role.active()).isFalse();
    assertThat(role.rowVersion()).isEqualTo(3L);
  }

  @Test
  void theAdministratorRoleStaysActiveAndKeepsRoleAdministration() {
    Role administrator =
        Role.restore(UUID.randomUUID(), "ADMINISTRATOR", "Administrator", null, true, 0L);

    assertThatThrownBy(() -> administrator.update("Administrator", null, false))
        .isInstanceOf(ProtectedRoleException.class);
    assertThat(administrator.active()).isTrue();
    assertThatThrownBy(() -> administrator.requireRevocable(List.of("PATIENT_VIEW", "ROLE_MANAGE")))
        .isInstanceOf(ProtectedRoleException.class);
    assertThatCode(() -> administrator.requireRevocable(List.of("AUDIT_LOG_VIEW")))
        .doesNotThrowAnyException();
    assertThatCode(() -> administrator.update("Admin", "Renamed", true)).doesNotThrowAnyException();
  }

  @Test
  void otherRolesMayLoseAnyPermissionAndBeDeactivated() {
    Role nurse = Role.restore(UUID.randomUUID(), "NURSE", "Nurse", null, true, 0L);

    assertThatCode(() -> nurse.requireRevocable(Role.ADMINISTRATOR_PROTECTED_PERMISSIONS))
        .doesNotThrowAnyException();
    assertThatCode(() -> nurse.update("Nurse", null, false)).doesNotThrowAnyException();
  }
}
