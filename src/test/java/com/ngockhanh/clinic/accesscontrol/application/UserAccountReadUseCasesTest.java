package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AssignableRoleResponse;
import com.ngockhanh.clinic.accesscontrol.application.service.UserAccountAccessPolicy;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CheckUsernameAvailabilityUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListAssignableRolesUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AssignableRole;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Username availability and assignable role list of the Create User Account screen. */
class UserAccountReadUseCasesTest {
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final UserAccountAccessPolicy access = new UserAccountAccessPolicy();

  private static UserPrincipal staff(String permission) {
    return UserPrincipal.builder()
        .userId(UUID.randomUUID())
        .principalType("STAFF")
        .roleAssignments(
            List.of(
                UserPrincipal.Assignment.builder()
                    .roleId(UUID.randomUUID())
                    .roleCode("ADMINISTRATOR")
                    .permissions(List.of(permission))
                    .build()))
        .build();
  }

  @Test
  void usernameAvailabilityIsTheAbsenceOfAnAccount() {
    var useCase = new CheckUsernameAvailabilityUseCase(access, accounts);
    UserPrincipal admin = staff(UserAccountAccessPolicy.USERNAME_CHECK_PERMISSION);
    when(accounts.existsByUsername("taken")).thenReturn(true);

    assertThat(useCase.execute("taken", admin).available()).isFalse();
    assertThat(useCase.execute("free", admin).available()).isTrue();
  }

  @Test
  void usernameCheckNeedsItsPermission() {
    var useCase = new CheckUsernameAvailabilityUseCase(access, accounts);

    assertThatThrownBy(
            () -> useCase.execute("user", staff(UserAccountAccessPolicy.CREATE_PERMISSION)))
        .isInstanceOf(ApplicationException.class);
    verifyNoInteractions(accounts);
  }

  @Test
  void assignableRolesAreTheActiveStaffRoles() {
    var useCase = new ListAssignableRolesUseCase(access, roles);
    AssignableRole nurse =
        new AssignableRole(UUID.randomUUID(), "NURSE", "Nurse", "Prepares", true);
    when(roles.findActiveStaffRoles()).thenReturn(List.of(nurse));

    assertThat(useCase.execute(staff(UserAccountAccessPolicy.ASSIGNABLE_ROLE_VIEW_PERMISSION)))
        .containsExactly(new AssignableRoleResponse(nurse.id(), "NURSE", "Nurse", "Prepares"));
  }

  @Test
  void assignableRolesNeedTheirPermission() {
    var useCase = new ListAssignableRolesUseCase(access, roles);

    assertThatThrownBy(() -> useCase.execute(staff(UserAccountAccessPolicy.CREATE_PERMISSION)))
        .isInstanceOf(ApplicationException.class);
    verifyNoInteractions(roles);
  }
}
