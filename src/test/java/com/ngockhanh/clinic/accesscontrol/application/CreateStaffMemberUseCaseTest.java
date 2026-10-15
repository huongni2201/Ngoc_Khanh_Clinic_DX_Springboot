package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateStaffMemberCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.service.StaffAdministrationPolicy;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateStaffMemberUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffMember;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.UsernameBase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.UserPasswordEncoder;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateStaffMemberUseCaseTest {
  private static final RoleRef DOCTOR = new RoleRef(UUID.randomUUID(), "GENERAL_PRACTITIONER");

  private final StaffMemberRepository staffMembers = mock(StaffMemberRepository.class);
  private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final UserPasswordEncoder passwords = new UserPasswordEncoder();
  private final CreateStaffMemberUseCase useCase =
      new CreateStaffMemberUseCase(
          new StaffAdministrationPolicy(),
          staffMembers,
          accounts,
          roles,
          passwords,
          audit,
          Clock.fixed(Instant.parse("2026-10-13T01:00:00Z"), ZoneOffset.UTC));

  @BeforeEach
  void allocateCodes() {
    when(staffMembers.nextStaffCode()).thenReturn("NV-0007");
    when(accounts.nextUsername(new UsernameBase("annv"))).thenReturn("annv2");
    when(roles.findActiveByCode("GENERAL_PRACTITIONER")).thenReturn(Optional.of(DOCTOR));
  }

  @Test
  void createsTheStaffMemberAndAccountWithGeneratedCodesAndAuditsBoth() {
    var admin = admin();

    var response = useCase.execute(command("GENERAL_PRACTITIONER"), admin);

    var savedAccount = ArgumentCaptor.forClass(StaffAccount.class);
    verify(staffMembers).save(any(StaffMember.class));
    verify(accounts).save(savedAccount.capture());
    assertThat(savedAccount.getValue().role()).isEqualTo(DOCTOR);
    assertThat(passwords.matches("Tam@12345", savedAccount.getValue().passwordHash())).isTrue();
    assertThat(response.staffCode()).isEqualTo("NV-0007");
    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(response.account().username()).isEqualTo("annv2");
    assertThat(response.account().roleCode()).isEqualTo("GENERAL_PRACTITIONER");
    assertThat(response.toString()).doesNotContain("Tam@12345", "{bcrypt}");
    verify(audit)
        .record(eq(admin.userId()), eq("CREATE_STAFF_MEMBER"), eq("STAFF_MEMBER"), eq(response.id()), isNull(), any());
    verify(audit)
        .record(
            eq(admin.userId()),
            eq("CREATE_ACCOUNT"),
            eq("ACCOUNT"),
            eq(response.account().id()),
            isNull(),
            any());
  }

  @Test
  void deniesCallersWithoutBothPermissionsBeforeAnyWrite() {
    var createOnly =
        UserPrincipal.builder()
            .userId(UUID.randomUUID())
            .principalType("STAFF")
            .roleAssignments(
                List.of(
                    new UserPrincipal.Assignment(
                        UUID.randomUUID(), "X", List.of(StaffAdministrationPolicy.CREATE_PERMISSION))))
            .build();

    assertThatThrownBy(() -> useCase.execute(command("GENERAL_PRACTITIONER"), createOnly))
        .isInstanceOf(ApplicationException.class)
        .extracting(error -> ((ApplicationException) error).type())
        .isEqualTo(ApplicationException.Type.ACCESS_DENIED);
    verifyNoInteractions(staffMembers, accounts, roles, audit);
  }

  @Test
  void rejectsAnUnknownOrPatientRoleAsUnprocessableWithoutWriting() {
    when(roles.findActiveByCode("GONE")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(command("GONE"), admin()))
        .isInstanceOf(UnprocessableEntityException.class);

    when(roles.findActiveByCode("PATIENT"))
        .thenReturn(Optional.of(new RoleRef(UUID.randomUUID(), "PATIENT")));
    assertThatThrownBy(() -> useCase.execute(command("PATIENT"), admin()))
        .isInstanceOf(UnprocessableEntityException.class);

    verify(staffMembers, never()).save(any());
    verify(accounts, never()).save(any());
    verify(audit, never()).record(any(), anyString(), anyString(), any(), any(), any());
  }

  @Test
  void leavesThePasswordFormatToTheRequestButNeverHashesMoreThanBcryptReads() {
    // 75 UTF-8 bytes: the encoder refuses it instead of silently truncating.
    assertThatThrownBy(() -> useCase.execute(withPassword("ệ".repeat(25)), admin()))
        .isInstanceOf(IllegalArgumentException.class);
    verify(staffMembers, never()).save(any());

    var saved = ArgumentCaptor.forClass(StaffAccount.class);
    useCase.execute(withPassword("A1!~" + "x".repeat(68)), admin());
    verify(accounts).save(saved.capture());
    assertThat(passwords.matches("A1!~" + "x".repeat(68), saved.getValue().passwordHash())).isTrue();
  }

  private static UserPrincipal admin() {
    return UserPrincipal.builder()
        .userId(UUID.randomUUID())
        .staffId(UUID.randomUUID())
        .principalType("STAFF")
        .roleAssignments(
            List.of(
                new UserPrincipal.Assignment(
                    UUID.randomUUID(),
                    "ADMINISTRATOR",
                    List.of(
                        StaffAdministrationPolicy.CREATE_PERMISSION,
                        StaffAdministrationPolicy.ROLE_ASSIGN_PERMISSION))))
        .build();
  }

  private static CreateStaffMemberCommand command(String roleCode) {
    return CreateStaffMemberCommand.builder()
        .fullName("Nguyễn Văn An")
        .initialPassword("Tam@12345")
        .roleCode(roleCode)
        .build();
  }

  private static CreateStaffMemberCommand withPassword(String password) {
    return CreateStaffMemberCommand.builder()
        .fullName("Nguyễn Văn An")
        .initialPassword(password)
        .roleCode("GENERAL_PRACTITIONER")
        .build();
  }
}
