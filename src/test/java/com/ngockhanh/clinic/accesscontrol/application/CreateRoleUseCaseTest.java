package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.port.out.RoleDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateRoleUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateRoleUseCaseTest {
  private final RoleRepository roles = mock(RoleRepository.class);
  private final RoleDirectory directory = mock(RoleDirectory.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final CreateRoleUseCase useCase = new CreateRoleUseCase(roles, directory, audit);
  private final UserPrincipal admin = ParticipantFixtures.staff("ROLE_CREATE");
  private final UserPrincipal copier =
      ParticipantFixtures.staff("ROLE_CREATE", "ROLE_PERMISSION_ASSIGN");

  @Test
  void createsAnActiveRoleByDefaultAndAuditsIt() {
    when(roles.existsByCode("LAB_TECH")).thenReturn(false);
    when(roles.existsByName("Lab technician", null)).thenReturn(false);
    when(directory.findSummary(any()))
        .thenAnswer(
            call ->
                Optional.of(
                    RoleSummary.builder().id(call.getArgument(0)).code("LAB_TECH").build()));

    RoleSummary created =
        useCase.execute(
            CreateRoleCommand.builder().code("LAB_TECH").name(" Lab technician ").build(), admin);

    var stored = ArgumentCaptor.forClass(Role.class);
    verify(roles).insert(stored.capture());
    assertThat(stored.getValue().active()).isTrue();
    assertThat(stored.getValue().name()).isEqualTo("Lab technician");
    assertThat(created.id()).isEqualTo(stored.getValue().id());
    verify(roles, never()).copyPermissions(any(), any());
    verify(audit)
        .record(
            eq(admin.userId()),
            eq("CREATE_ROLE"),
            eq("ROLE"),
            eq(stored.getValue().id()),
            isNull(),
            eq(expectedAudit()));
  }

  @Test
  void copiesThePermissionsOfAnActiveSourceRole() {
    Role source = Role.restore(UUID.randomUUID(), "NURSE", "Nurse", null, true, 2L);
    when(roles.existsByCode(any())).thenReturn(false);
    when(roles.existsByName(any(), any())).thenReturn(false);
    when(roles.findById(source.id())).thenReturn(Optional.of(source));
    when(roles.findGrantedPermissionCodes(any())).thenReturn(List.of("PATIENT_VIEW"));
    when(directory.findSummary(any())).thenReturn(Optional.of(RoleSummary.builder().build()));

    useCase.execute(
        CreateRoleCommand.builder()
            .code("HEAD_NURSE")
            .name("Head nurse")
            .copyFromRoleId(source.id())
            .active(false)
            .build(),
        copier);

    var stored = ArgumentCaptor.forClass(Role.class);
    verify(roles).insert(stored.capture());
    assertThat(stored.getValue().active()).isFalse();
    verify(roles).copyPermissions(source.id(), stored.getValue().id());
    var after = ArgumentCaptor.forClass(Map.class);
    verify(audit).record(any(), eq("CREATE_ROLE"), eq("ROLE"), any(), isNull(), after.capture());
    assertThat(after.getValue())
        .containsEntry("copiedFromRoleCode", "NURSE")
        .containsEntry("granted", List.of("PATIENT_VIEW"));
  }

  @Test
  void copyingPermissionsAlsoRequiresThePermissionToAssignThem() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    CreateRoleCommand.builder()
                        .code("HEAD_NURSE")
                        .name("Head nurse")
                        .copyFromRoleId(UUID.randomUUID())
                        .build(),
                    admin))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            denied -> assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(roles, audit);
  }

  private static Map<String, Object> expectedAudit() {
    Map<String, Object> after = new HashMap<>();
    after.put("code", "LAB_TECH");
    after.put("name", "Lab technician");
    after.put("description", null);
    after.put("active", true);
    after.put("granted", List.of());
    return after;
  }

  @Test
  void rejectsAnInactiveUnknownOrPatientSourceRoleWithoutWriting() {
    Role inactive = Role.restore(UUID.randomUUID(), "NURSE", "Nurse", null, false, 0L);
    Role patient = Role.restore(UUID.randomUUID(), "PATIENT", "Patient", null, true, 0L);
    when(roles.existsByCode(any())).thenReturn(false);
    when(roles.existsByName(any(), any())).thenReturn(false);
    when(roles.findById(inactive.id())).thenReturn(Optional.of(inactive));
    when(roles.findById(patient.id())).thenReturn(Optional.of(patient));

    for (UUID source : new UUID[] {inactive.id(), patient.id(), UUID.randomUUID()})
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      CreateRoleCommand.builder()
                          .code("HEAD_NURSE")
                          .name("Head nurse")
                          .copyFromRoleId(source)
                          .build(),
                      copier))
          .isInstanceOf(UnprocessableEntityException.class);
    verify(roles, never()).insert(any());
    verifyNoInteractions(audit);
  }

  @Test
  void reportsTheDuplicatedField() {
    when(roles.existsByCode("NURSE")).thenReturn(true);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    CreateRoleCommand.builder().code("NURSE").name("Other").build(), admin))
        .isInstanceOfSatisfying(
            FieldConflictException.class,
            conflict -> assertThat(conflict.field()).isEqualTo("code"));

    when(roles.existsByCode("NURSE_2")).thenReturn(false);
    when(roles.existsByName("nurse", null)).thenReturn(true);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    CreateRoleCommand.builder().code("NURSE_2").name("nurse").build(), admin))
        .isInstanceOfSatisfying(
            FieldConflictException.class,
            conflict -> assertThat(conflict.field()).isEqualTo("name"));
    verify(roles, never()).insert(any());
  }

  @Test
  void requiresTheCreatePermissionOnAStaffAccount() {
    for (UserPrincipal caller :
        new UserPrincipal[] {null, ParticipantFixtures.staff("ROLE_MANAGE", "ROLE_UPDATE")})
      assertThatThrownBy(
              () ->
                  useCase.execute(CreateRoleCommand.builder().code("X").name("X").build(), caller))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(roles, audit);
  }
}
