package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.command.UpdateRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.port.out.RoleDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.UpdateRoleUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.exception.ProtectedRoleException;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateRoleUseCaseTest {
  private final RoleRepository roles = mock(RoleRepository.class);
  private final RoleDirectory directory = mock(RoleDirectory.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final UpdateRoleUseCase useCase = new UpdateRoleUseCase(roles, directory, audit);
  private final UserPrincipal admin = ParticipantFixtures.staff("ROLE_UPDATE");

  @Test
  void deactivatesTheRoleWithTheExpectedVersionAndAuditsBeforeAndAfter() {
    Role nurse = Role.restore(UUID.randomUUID(), "NURSE", "Nurse", null, true, 4L);
    when(roles.findById(nurse.id())).thenReturn(Optional.of(nurse));
    when(roles.existsByName("Nurse", nurse.id())).thenReturn(false);
    when(directory.findSummary(nurse.id()))
        .thenReturn(Optional.of(RoleSummary.builder().id(nurse.id()).rowVersion(5L).build()));

    RoleSummary updated =
        useCase.execute(
            nurse.id(),
            UpdateRoleCommand.builder().name("Nurse").active(false).rowVersion(4L).build(),
            admin);

    assertThat(updated.rowVersion()).isEqualTo(5L);
    var stored = ArgumentCaptor.forClass(Role.class);
    verify(roles).update(stored.capture(), eq(4L));
    assertThat(stored.getValue().active()).isFalse();
    verify(audit)
        .record(
            eq(admin.userId()), eq("UPDATE_ROLE"), eq("ROLE"), eq(nurse.id()), anyMap(), anyMap());
  }

  @Test
  void rejectsAStaleVersionBeforeMutation() {
    Role nurse = Role.restore(UUID.randomUUID(), "NURSE", "Nurse", null, true, 4L);
    when(roles.findById(nurse.id())).thenReturn(Optional.of(nurse));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    nurse.id(),
                    UpdateRoleCommand.builder().name("Nurse").active(true).rowVersion(3L).build(),
                    admin))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(roles, never()).update(any(), any());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsANameUsedByAnotherRole() {
    Role nurse = Role.restore(UUID.randomUUID(), "NURSE", "Nurse", null, true, 0L);
    when(roles.findById(nurse.id())).thenReturn(Optional.of(nurse));
    when(roles.existsByName("Receptionist", nurse.id())).thenReturn(true);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    nurse.id(),
                    UpdateRoleCommand.builder()
                        .name("Receptionist")
                        .active(true)
                        .rowVersion(0L)
                        .build(),
                    admin))
        .isInstanceOf(FieldConflictException.class);
    verify(roles, never()).update(any(), any());
  }

  @Test
  void neverDeactivatesTheAdministratorRole() {
    Role administrator =
        Role.restore(UUID.randomUUID(), "ADMINISTRATOR", "Administrator", null, true, 0L);
    when(roles.findById(administrator.id())).thenReturn(Optional.of(administrator));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    administrator.id(),
                    UpdateRoleCommand.builder()
                        .name("Administrator")
                        .active(false)
                        .rowVersion(0L)
                        .build(),
                    admin))
        .isInstanceOf(ProtectedRoleException.class);
    verify(roles, never()).update(any(), any());
  }

  @Test
  void reportsAnUnknownRole() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    UUID.randomUUID(),
                    UpdateRoleCommand.builder().name("X").active(true).rowVersion(0L).build(),
                    admin))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
