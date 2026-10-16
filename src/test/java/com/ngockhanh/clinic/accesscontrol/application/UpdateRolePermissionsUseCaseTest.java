package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.command.UpdateRolePermissionsCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.RolePermissionsResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.UpdateRolePermissionsUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.exception.ProtectedRoleException;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateRolePermissionsUseCaseTest {
  private final RoleRepository roles = mock(RoleRepository.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final UpdateRolePermissionsUseCase useCase =
      new UpdateRolePermissionsUseCase(roles, audit);
  private final UserPrincipal admin = ParticipantFixtures.staff("ROLE_PERMISSION_ASSIGN");
  private final Role receptionist =
      Role.restore(UUID.randomUUID(), "RECEPTIONIST", "Receptionist", null, true, 7L);

  @Test
  void appliesOnlyRealChangesAndAuditsThem() {
    when(roles.findById(receptionist.id())).thenReturn(Optional.of(receptionist));
    when(roles.countExistingPermissions(anyCollection())).thenReturn(3L);
    when(roles.findGrantedPermissionCodes(receptionist.id()))
        .thenReturn(
            List.of("PAYMENT_REFUND", "PAYMENT_VIEW"),
            List.of("PAYMENT_VIEW", "PRESCRIPTION_PRINT"));

    RolePermissionsResponse response =
        useCase.execute(
            receptionist.id(),
            UpdateRolePermissionsCommand.builder()
                .rowVersion(7L)
                .grant(List.of("PRESCRIPTION_PRINT", " PAYMENT_VIEW "))
                .revoke(List.of("PAYMENT_REFUND"))
                .build(),
            admin);

    verify(roles).incrementRowVersion(receptionist.id(), 7L);
    verify(roles).grantPermissions(receptionist.id(), List.of("PRESCRIPTION_PRINT"));
    verify(roles).revokePermissions(receptionist.id(), List.of("PAYMENT_REFUND"));
    assertThat(response.rowVersion()).isEqualTo(8L);
    assertThat(response.permissionCodes()).containsExactly("PAYMENT_VIEW", "PRESCRIPTION_PRINT");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
    verify(audit)
        .record(
            eq(admin.userId()),
            eq("UPDATE_ROLE_PERMISSIONS"),
            eq("ROLE"),
            eq(receptionist.id()),
            any(),
            after.capture());
    assertThat(after.getValue())
        .containsEntry("granted", List.of("PRESCRIPTION_PRINT"))
        .containsEntry("removed", List.of("PAYMENT_REFUND"))
        .containsEntry("rowVersion", 8L);
  }

  @Test
  void rejectsAStaleVersionAnInactiveRoleAndUnknownCodesWithoutWriting() {
    Role inactive = Role.restore(UUID.randomUUID(), "NURSE", "Nurse", null, false, 0L);
    when(roles.findById(receptionist.id())).thenReturn(Optional.of(receptionist));
    when(roles.findById(inactive.id())).thenReturn(Optional.of(inactive));
    when(roles.countExistingPermissions(anyCollection())).thenReturn(0L);

    assertThatThrownBy(() -> useCase.execute(receptionist.id(), grant(6L, "PAYMENT_VIEW"), admin))
        .isInstanceOf(ConcurrentUpdateException.class);
    assertThatThrownBy(() -> useCase.execute(inactive.id(), grant(0L, "PAYMENT_VIEW"), admin))
        .isInstanceOf(UnprocessableEntityException.class);
    assertThatThrownBy(() -> useCase.execute(receptionist.id(), grant(7L, "NOT_A_CODE"), admin))
        .isInstanceOf(UnprocessableEntityException.class);
    verify(roles, never()).incrementRowVersion(any(), any());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsEmptyOrContradictoryChanges() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    receptionist.id(),
                    UpdateRolePermissionsCommand.builder().rowVersion(7L).build(),
                    admin))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    receptionist.id(),
                    UpdateRolePermissionsCommand.builder()
                        .rowVersion(7L)
                        .grant(List.of("PAYMENT_VIEW"))
                        .revoke(List.of("PAYMENT_VIEW"))
                        .build(),
                    admin))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(roles, audit);
  }

  @Test
  void keepsRoleAdministrationOnTheAdministratorRole() {
    Role administrator =
        Role.restore(UUID.randomUUID(), "ADMINISTRATOR", "Administrator", null, true, 0L);
    when(roles.findById(administrator.id())).thenReturn(Optional.of(administrator));
    when(roles.countExistingPermissions(anyCollection())).thenReturn(1L);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    administrator.id(),
                    UpdateRolePermissionsCommand.builder()
                        .rowVersion(0L)
                        .revoke(List.of("ROLE_PERMISSION_ASSIGN"))
                        .build(),
                    admin))
        .isInstanceOf(ProtectedRoleException.class);
    verify(roles, never()).revokePermissions(any(), any());
  }

  private static UpdateRolePermissionsCommand grant(Long rowVersion, String code) {
    return UpdateRolePermissionsCommand.builder()
        .rowVersion(rowVersion)
        .grant(List.of(code))
        .build();
  }
}
