package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.command.ChangeAccountRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountRoleResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ChangeAccountRoleUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.entity.AccountRole;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangeAccountRoleUseCaseTest {
  private static final RoleRef RECEPTIONIST = new RoleRef(UUID.randomUUID(), "RECEPTIONIST");
  private static final RoleRef NURSE = new RoleRef(UUID.randomUUID(), "NURSE");
  private static final RoleRef ADMINISTRATOR = new RoleRef(UUID.randomUUID(), "ADMINISTRATOR");
  private static final RoleRef PATIENT = new RoleRef(UUID.randomUUID(), "PATIENT");

  private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final ChangeAccountRoleUseCase useCase =
      new ChangeAccountRoleUseCase(accounts, roles, audit);
  private final UserPrincipal admin = ParticipantFixtures.staff("USER_ROLE_ASSIGN");
  private final UUID accountId = UUID.randomUUID();

  private void stored(RoleRef role, AccountType type, AccountStatus status) {
    when(accounts.findAccountRole(accountId))
        .thenReturn(Optional.of(new AccountRole(accountId, type, status, role, 3L)));
  }

  private AccountRoleResponse change(String roleCode, Long rowVersion) {
    return useCase.execute(
        accountId,
        ChangeAccountRoleCommand.builder().roleCode(roleCode).rowVersion(rowVersion).build(),
        admin);
  }

  @Test
  void replacesTheRoleWithTheExpectedVersionAndAuditsBeforeAndAfter() {
    stored(RECEPTIONIST, AccountType.STAFF, AccountStatus.ACTIVE);
    when(roles.findActiveByCode("NURSE")).thenReturn(Optional.of(NURSE));

    AccountRoleResponse changed = change(" NURSE ", 3L);

    assertThat(changed.roleCode()).isEqualTo("NURSE");
    assertThat(changed.rowVersion()).isEqualTo(4L);
    verify(accounts).changeRole(accountId, NURSE, 3L);
    verify(accounts, never()).lockAdministratorAccounts();
    verify(audit)
        .record(
            admin.userId(),
            "CHANGE_ACCOUNT_ROLE",
            "ACCOUNT",
            accountId,
            Map.of("roleCode", "RECEPTIONIST", "rowVersion", 3L),
            Map.of("roleCode", "NURSE", "rowVersion", 4L));
  }

  @Test
  void keepsTheLastAdministratorAccountUnderTheLock() {
    stored(ADMINISTRATOR, AccountType.STAFF, AccountStatus.ACTIVE);
    when(roles.findActiveByCode("NURSE")).thenReturn(Optional.of(NURSE));
    when(accounts.countOtherActiveAdministrators(accountId)).thenReturn(0L);

    assertThatThrownBy(() -> change("NURSE", 3L))
        .isInstanceOfSatisfying(
            FieldConflictException.class,
            conflict -> {
              assertThat(conflict.field()).isEqualTo("roleCode");
              assertThat(conflict.code()).isEqualTo("LastAdministrator");
            });
    var order = inOrder(accounts);
    order.verify(accounts).lockAdministratorAccounts();
    order.verify(accounts).countOtherActiveAdministrators(accountId);
    verify(accounts, never()).changeRole(any(), any(), any());
    verifyNoInteractions(audit);
  }

  @Test
  void lowersAnAdministratorWhileAnotherOneRemains() {
    stored(ADMINISTRATOR, AccountType.STAFF, AccountStatus.ACTIVE);
    when(roles.findActiveByCode("NURSE")).thenReturn(Optional.of(NURSE));
    when(accounts.countOtherActiveAdministrators(accountId)).thenReturn(1L);

    change("NURSE", 3L);

    verify(accounts).changeRole(accountId, NURSE, 3L);
  }

  @Test
  void rejectsAStaleVersionBeforeLookingAtTheRole() {
    stored(RECEPTIONIST, AccountType.STAFF, AccountStatus.ACTIVE);

    assertThatThrownBy(() -> change("NURSE", 2L)).isInstanceOf(ConcurrentUpdateException.class);
    verifyNoInteractions(roles, audit);
  }

  @Test
  void rejectsAnUnavailableRoleTheCurrentRoleAndAnAccountOutOfScope() {
    stored(RECEPTIONIST, AccountType.STAFF, AccountStatus.ACTIVE);
    when(roles.findActiveByCode("PATIENT")).thenReturn(Optional.of(PATIENT));
    when(roles.findActiveByCode("RECEPTIONIST")).thenReturn(Optional.of(RECEPTIONIST));

    assertThatThrownBy(() -> change("CLOSED_ROLE", 3L))
        .isInstanceOfSatisfying(
            UnprocessableEntityException.class,
            rejected -> assertThat(rejected.code()).isEqualTo("UnavailableRole"));
    assertThatThrownBy(() -> change("PATIENT", 3L))
        .isInstanceOf(UnprocessableEntityException.class);
    assertThatThrownBy(() -> change("RECEPTIONIST", 3L))
        .isInstanceOf(IllegalArgumentException.class);

    stored(RECEPTIONIST, AccountType.STAFF, AccountStatus.LOCKED);
    assertThatThrownBy(() -> change("NURSE", 3L))
        .isInstanceOfSatisfying(
            UnprocessableEntityException.class,
            rejected -> assertThat(rejected.field()).isEqualTo("accountId"));
    stored(PATIENT, AccountType.PATIENT, AccountStatus.ACTIVE);
    assertThatThrownBy(() -> change("NURSE", 3L)).isInstanceOf(UnprocessableEntityException.class);

    when(accounts.findAccountRole(accountId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> change("NURSE", 3L)).isInstanceOf(ResourceNotFoundException.class);
    verify(accounts, never()).changeRole(any(), any(), any());
    verifyNoInteractions(audit);
  }

  @Test
  void requiresTheAssignPermissionOnAStaffAccount() {
    for (UserPrincipal caller :
        new UserPrincipal[] {null, ParticipantFixtures.staff("USER_ACCOUNT_CREATE")})
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      accountId,
                      ChangeAccountRoleCommand.builder().roleCode("NURSE").rowVersion(0L).build(),
                      caller))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(accounts, roles, audit);
  }
}
