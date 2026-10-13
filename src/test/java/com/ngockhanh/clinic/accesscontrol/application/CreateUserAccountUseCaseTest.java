package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateUserAccountCommand;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SignInCredentialSender;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SignInCredentialSender.SignInCredentialMessage;
import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory;
import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory.PatientAccountOwner;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.CreateUserAccountResponse;
import com.ngockhanh.clinic.accesscontrol.application.service.UserAccountAccessPolicy;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateUserAccountUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.entity.NewUserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AccountOwner;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AssignableRole;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class CreateUserAccountUseCaseTest {
  private static final String PASSWORD = "Abcdef1!";
  private static final String HASH = "synthetic-password-hash";
  private static final String USERNAME = "nguyen.van.a";
  private static final String PHONE = "0912 345 678";
  private static final Instant NOW = Instant.parse("2026-10-13T03:00:00.123456Z");

  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final StaffMemberRepository staffMembers = mock(StaffMemberRepository.class);
  private final PatientAccountOwnerDirectory patients = mock(PatientAccountOwnerDirectory.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final PasswordEncoder passwords = mock(PasswordEncoder.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final SignInCredentialSender sender = mock(SignInCredentialSender.class);
  private final RecordingTransactions transactions = new RecordingTransactions();
  private final List<String> events = new ArrayList<>();

  private final UUID adminId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();
  private final AssignableRole doctor =
      new AssignableRole(
          UUID.randomUUID(), "GENERAL_PRACTITIONER", "General Practitioner", "", true);
  private final AssignableRole patientRole =
      new AssignableRole(UUID.randomUUID(), "PATIENT", "Patient", "", true);

  private final CreateUserAccountUseCase useCase =
      new CreateUserAccountUseCase(
          new UserAccountAccessPolicy(),
          accounts,
          staffMembers,
          patients,
          roles,
          passwords,
          audit,
          sender,
          transactions,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void collaborators() {
    when(passwords.encode(PASSWORD))
        .thenAnswer(
            call -> {
              events.add("encode in transaction=" + transactions.active);
              return HASH;
            });
    when(staffMembers.lockForAccountLink(ownerId))
        .thenReturn(Optional.of(new AccountOwner(ownerId, AccountType.STAFF, true, PHONE)));
    when(patients.lockForAccountLink(ownerId))
        .thenReturn(Optional.of(new PatientAccountOwner(ownerId, true, "0987111222")));
    when(roles.lockByIds(Set.of(doctor.id()))).thenReturn(List.of(doctor));
    when(roles.findByCode("PATIENT")).thenReturn(Optional.of(patientRole));
    when(accounts.insertIfAbsent(any())).thenReturn(true);
    when(sender.send(any()))
        .thenAnswer(
            call -> {
              events.add("send in transaction=" + transactions.active);
              return true;
            });
  }

  private UserPrincipal admin(String... permissions) {
    return UserPrincipal.builder()
        .userId(adminId)
        .principalType("STAFF")
        .roleAssignments(
            List.of(
                UserPrincipal.Assignment.builder()
                    .roleId(UUID.randomUUID())
                    .roleCode("ADMINISTRATOR")
                    .permissions(List.of(permissions))
                    .build()))
        .build();
  }

  private UserPrincipal admin() {
    return admin(UserAccountAccessPolicy.CREATE_PERMISSION);
  }

  private CreateUserAccountCommand staffCommand(List<UUID> roleIds) {
    return CreateUserAccountCommand.builder()
        .accountType("STAFF")
        .ownerId(ownerId)
        .username(USERNAME)
        .temporaryPassword(PASSWORD)
        .roleIds(roleIds)
        .build();
  }

  private CreateUserAccountCommand patientCommand() {
    return CreateUserAccountCommand.builder()
        .accountType("PATIENT")
        .ownerId(ownerId)
        .username("le.thi.hoa")
        .temporaryPassword(PASSWORD)
        .roleIds(List.of())
        .build();
  }

  @Test
  void createsAStaffAccountGrantsItsRolesAuditsAndSendsTheSmsAfterCommit() {
    CreateUserAccountResponse response =
        useCase.execute(staffCommand(List.of(doctor.id())), admin());

    ArgumentCaptor<NewUserAccount> inserted = ArgumentCaptor.forClass(NewUserAccount.class);
    verify(accounts).insertIfAbsent(inserted.capture());
    NewUserAccount account = inserted.getValue();
    assertThat(account.staffMemberId()).isEqualTo(ownerId);
    assertThat(account.passwordHash()).isEqualTo(HASH);
    assertThat(account.createdAt()).isEqualTo(Instant.parse("2026-10-13T03:00:00.123Z"));
    verify(accounts).grantRoles(account.id(), List.of(doctor.id()), adminId, account.createdAt());
    assertThat(response.accountId()).isEqualTo(account.id());
    assertThat(response.accountType()).isEqualTo("STAFF");
    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(response.ownerId()).isEqualTo(ownerId);
    assertThat(response.roleCodes()).containsExactly("GENERAL_PRACTITIONER");
    assertThat(response.smsDelivered()).isTrue();
    assertThat(events).containsExactly("encode in transaction=false", "send in transaction=false");

    ArgumentCaptor<SignInCredentialMessage> sms =
        ArgumentCaptor.forClass(SignInCredentialMessage.class);
    verify(sender).send(sms.capture());
    assertThat(sms.getValue().phoneNumber()).isEqualTo("0912345678");
    assertThat(sms.getValue().username()).isEqualTo(USERNAME);
    assertThat(sms.getValue().temporaryPassword()).isEqualTo(PASSWORD);
  }

  @Test
  @SuppressWarnings("unchecked")
  void auditKeepsNoUsernamePhoneOrPassword() {
    useCase.execute(staffCommand(List.of(doctor.id())), admin());

    ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            eq(adminId),
            eq("CREATE_USER_ACCOUNT"),
            eq("ACCOUNT"),
            any(),
            eq(null),
            after.capture());
    Map<String, Object> snapshot = (Map<String, Object>) after.getValue();
    assertThat(snapshot)
        .containsEntry("accountType", "STAFF")
        .containsEntry("staffMemberId", ownerId)
        .containsEntry("patientId", null)
        .containsEntry("status", "ACTIVE")
        .containsEntry("roleCodes", List.of("GENERAL_PRACTITIONER"));
    assertThat(snapshot.toString())
        .doesNotContain(USERNAME)
        .doesNotContain(PASSWORD)
        .doesNotContain("0912");
  }

  @Test
  void createsAPatientAccountWithThePatientRole() {
    CreateUserAccountResponse response = useCase.execute(patientCommand(), admin());

    ArgumentCaptor<NewUserAccount> inserted = ArgumentCaptor.forClass(NewUserAccount.class);
    verify(accounts).insertIfAbsent(inserted.capture());
    assertThat(inserted.getValue().patientId()).isEqualTo(ownerId);
    assertThat(response.roleCodes()).containsExactly("PATIENT");
    verify(accounts).grantRoles(any(), eq(List.of(patientRole.id())), eq(adminId), any());
    verify(staffMembers, never()).lockForAccountLink(any());
  }

  @Test
  void anUndeliveredOrFailedSmsKeepsTheAccount() {
    when(sender.send(any())).thenReturn(false);
    assertThat(useCase.execute(staffCommand(List.of(doctor.id())), admin()).smsDelivered())
        .isFalse();

    when(sender.send(any())).thenReturn(null);
    assertThat(useCase.execute(staffCommand(List.of(doctor.id())), admin()).smsDelivered())
        .isFalse();

    when(sender.send(any())).thenThrow(new IllegalStateException("gateway down"));
    assertThat(useCase.execute(staffCommand(List.of(doctor.id())), admin()).smsDelivered())
        .isFalse();
  }

  @Test
  void callersWithoutThePermissionOrPatientPrincipalsAreDenied() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    staffCommand(List.of(doctor.id())),
                    admin(UserAccountAccessPolicy.OWNER_SEARCH_PERMISSION)))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            denied -> assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    UserPrincipal patient =
        UserPrincipal.builder()
            .userId(UUID.randomUUID())
            .principalType("PATIENT")
            .roleAssignments(admin().roleAssignments())
            .build();
    assertThatThrownBy(() -> useCase.execute(staffCommand(List.of(doctor.id())), patient))
        .isInstanceOf(ApplicationException.class);
    verifyNoInteractions(staffMembers, patients, roles, audit, sender);
    verify(accounts, never()).insertIfAbsent(any());
  }

  @Test
  void anyNonBlankPasswordIsAcceptedWithoutAComplexityPolicy() {
    when(passwords.encode("abc")).thenReturn(HASH);
    CreateUserAccountCommand simple =
        CreateUserAccountCommand.builder()
            .accountType("STAFF")
            .ownerId(ownerId)
            .username(USERNAME)
            .temporaryPassword("abc")
            .roleIds(List.of(doctor.id()))
            .build();

    useCase.execute(simple, admin());

    verify(passwords).encode("abc");
    verify(accounts).insertIfAbsent(any());
  }

  @Test
  void aBlankPasswordIsRejectedBeforeHashing() {
    CreateUserAccountCommand blank =
        CreateUserAccountCommand.builder()
            .accountType("STAFF")
            .ownerId(ownerId)
            .username(USERNAME)
            .temporaryPassword("   ")
            .roleIds(List.of(doctor.id()))
            .build();

    assertThatThrownBy(() -> useCase.execute(blank, admin()))
        .isInstanceOf(IllegalArgumentException.class);
    verify(passwords, never()).encode(anyString());
    assertNothingWritten();
  }

  @Test
  void aPasswordTheEncoderRejectsWritesNothing() {
    doThrow(new IllegalArgumentException("too long")).when(passwords).encode(PASSWORD);

    assertThatThrownBy(() -> useCase.execute(staffCommand(List.of(doctor.id())), admin()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(staffMembers);
    assertNothingWritten();
  }

  @Test
  void roleSelectionMustMatchTheAccountType() {
    assertInvalid(staffCommand(List.of()), CreateUserAccountUseCase.STAFF_ROLE_REQUIRED);
    assertInvalid(
        staffCommand(List.of(doctor.id(), doctor.id())), CreateUserAccountUseCase.DUPLICATE_ROLE);
    CreateUserAccountCommand patientWithRoles =
        CreateUserAccountCommand.builder()
            .accountType("PATIENT")
            .ownerId(ownerId)
            .username(USERNAME)
            .temporaryPassword(PASSWORD)
            .roleIds(List.of(doctor.id()))
            .build();
    assertInvalid(patientWithRoles, CreateUserAccountUseCase.PATIENT_ROLES_FORBIDDEN);
  }

  @Test
  void aMissingOwnerIsNotFound() {
    when(staffMembers.lockForAccountLink(ownerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(staffCommand(List.of(doctor.id())), admin()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertNothingWritten();
  }

  @Test
  void anInactiveOwnerIsAConflict() {
    when(staffMembers.lockForAccountLink(ownerId))
        .thenReturn(Optional.of(new AccountOwner(ownerId, AccountType.STAFF, false, PHONE)));

    assertConflict(staffCommand(List.of(doctor.id())), CreateUserAccountUseCase.OWNER_INACTIVE);
  }

  @Test
  void anOwnerWithAnAccountIsAConflict() {
    when(accounts.isOwnerLinked(AccountType.STAFF, ownerId)).thenReturn(true);

    assertConflict(staffCommand(List.of(doctor.id())), CreateUserAccountUseCase.OWNER_LINKED);
  }

  @Test
  void anOwnerWithoutAMobileNumberIsAConflict() {
    when(staffMembers.lockForAccountLink(ownerId))
        .thenReturn(
            Optional.of(new AccountOwner(ownerId, AccountType.STAFF, true, "024 3123 4567")));

    assertConflict(
        staffCommand(List.of(doctor.id())), CreateUserAccountUseCase.OWNER_WITHOUT_MOBILE);
  }

  @Test
  void aTakenUsernameIsAConflict() {
    when(accounts.existsByUsername(USERNAME)).thenReturn(true);

    assertConflict(staffCommand(List.of(doctor.id())), CreateUserAccountUseCase.USERNAME_TAKEN);
  }

  @Test
  void missingInactiveOrPatientRolesCannotBeGrantedToStaff() {
    UUID unknown = UUID.randomUUID();
    when(roles.lockByIds(Set.of(unknown))).thenReturn(List.of());
    assertConflict(staffCommand(List.of(unknown)), CreateUserAccountUseCase.ROLE_NOT_ASSIGNABLE);

    AssignableRole inactive =
        new AssignableRole(UUID.randomUUID(), "RECEPTIONIST", "Receptionist", "", false);
    when(roles.lockByIds(Set.of(inactive.id()))).thenReturn(List.of(inactive));
    assertConflict(
        staffCommand(List.of(inactive.id())), CreateUserAccountUseCase.ROLE_NOT_ASSIGNABLE);

    when(roles.lockByIds(Set.of(patientRole.id()))).thenReturn(List.of(patientRole));
    assertConflict(
        staffCommand(List.of(patientRole.id())), CreateUserAccountUseCase.ROLE_NOT_ASSIGNABLE);
  }

  @Test
  void aConcurrentlyTakenUsernameIsReportedAfterTheInsertConflict() {
    when(accounts.insertIfAbsent(any())).thenReturn(false);
    when(accounts.existsByUsername(USERNAME)).thenReturn(false, true);

    assertThatThrownBy(() -> useCase.execute(staffCommand(List.of(doctor.id())), admin()))
        .isInstanceOf(ConflictException.class)
        .hasMessage(CreateUserAccountUseCase.USERNAME_TAKEN);
    verify(accounts, never()).grantRoles(any(), any(), any(), any());
    verifyNoInteractions(audit, sender);
  }

  @Test
  void anAuditFailureSendsNoSms() {
    doThrow(new IllegalStateException("audit failed"))
        .when(audit)
        .record(any(), anyString(), anyString(), any(), any(), any());

    assertThatThrownBy(() -> useCase.execute(staffCommand(List.of(doctor.id())), admin()))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(sender);
  }

  private void assertInvalid(CreateUserAccountCommand command, String message) {
    assertThatThrownBy(() -> useCase.execute(command, admin()))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            invalid -> {
              assertThat(invalid.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT);
              assertThat(invalid.getMessage()).isEqualTo(message);
            });
    assertNothingWritten();
  }

  private void assertConflict(CreateUserAccountCommand command, String message) {
    assertThatThrownBy(() -> useCase.execute(command, admin()))
        .isInstanceOf(ConflictException.class)
        .hasMessage(message);
    assertNothingWritten();
  }

  private void assertNothingWritten() {
    verify(accounts, never()).insertIfAbsent(any());
    verify(accounts, never()).grantRoles(any(), any(), any(), any());
    verifyNoInteractions(audit, sender);
  }

  /** Runs the callback directly and records whether a transaction is open. */
  private static final class RecordingTransactions implements TransactionOperations {
    Boolean active = false;

    @Override
    public <T> T execute(TransactionCallback<T> action) {
      active = true;
      try {
        TransactionStatus status = new SimpleTransactionStatus();
        return action.doInTransaction(status);
      } finally {
        active = false;
      }
    }
  }
}
