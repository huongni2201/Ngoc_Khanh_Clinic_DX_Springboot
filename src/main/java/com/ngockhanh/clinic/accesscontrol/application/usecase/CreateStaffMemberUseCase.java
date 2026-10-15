package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateStaffMemberCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.StaffMemberSummary;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.service.StaffAdministrationPolicy;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffMember;
import com.ngockhanh.clinic.accesscontrol.domain.enums.Sex;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.UsernameBase;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a staff member together with its STAFF account (docs/api/staff-members.md).
 *
 * <p>The staff code ({@code NV-<n>}) and the username (name initials plus the next free number)
 * are generated under transaction-scoped locks. The account holds exactly one role. The staff
 * member, the account and both audit events are written in one transaction: if any write or the
 * audit fails, nothing is kept.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateStaffMemberUseCase {
  static final String CREATE_STAFF_MEMBER = "CREATE_STAFF_MEMBER";
  static final String CREATE_ACCOUNT = "CREATE_ACCOUNT";
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final StaffAdministrationPolicy policy;
  private final StaffMemberRepository staffMembers;
  private final StaffAccountRepository accounts;
  private final RoleRepository roles;
  private final PasswordEncoder passwords;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Creates an {@code ACTIVE} staff member and an {@code ACTIVE} STAFF account holding the role.
   *
   * @param command staff member details, temporary password and role code
   * @param principal authenticated caller, recorded as the audit actor
   * @return the created staff member with its account, without the password
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code USER_ACCOUNT_CREATE}
   *     and {@code USER_ROLE_ASSIGN}
   * @throws IllegalArgumentException when the details, password or role code are invalid
   * @throws UnprocessableEntityException (HTTP 422) when the role code is unknown, inactive or
   *     {@code PATIENT}
   */
  @Transactional
  public StaffMemberSummary execute(CreateStaffMemberCommand command, UserPrincipal principal) {
    policy.requireCreate(principal);
    if (command == null) throw new IllegalArgumentException("Staff member command is required");
    if (command.roleCode() == null || command.roleCode().isBlank())
      throw new IllegalArgumentException("Role code is required");

    RoleRef role =
        roles
            .findActiveByCode(command.roleCode().trim())
            .filter(StaffAccount::isAssignable)
            .orElseThrow(() -> new UnprocessableEntityException("The role is not available"));

    String passwordHash = hash(command.initialPassword());
    Sex sex = command.sex() == null ? null : Sex.valueOf(command.sex());
    UsernameBase usernameBase = UsernameBase.from(command.fullName());

    StaffMember staffMember =
        StaffMember.create(
            UuidV7Generator.generate(),
            staffMembers.nextStaffCode(),
            command.fullName(),
            command.dateOfBirth(),
            sex,
            command.phone(),
            command.email(),
            LocalDate.now(clock.withZone(BUSINESS_ZONE)));
    staffMembers.save(staffMember);

    StaffAccount account =
        StaffAccount.open(
            UuidV7Generator.generate(),
            staffMember.id(),
            accounts.nextUsername(usernameBase),
            passwordHash,
            role);
    accounts.save(account);

    audit.record(
        principal.userId(),
        CREATE_STAFF_MEMBER,
        "STAFF_MEMBER",
        staffMember.id(),
        null,
        Map.of("staffCode", staffMember.staffCode(), "accountId", account.id()));
    audit.record(
        principal.userId(),
        CREATE_ACCOUNT,
        "ACCOUNT",
        account.id(),
        null,
        Map.of(
            "staffMemberId", staffMember.id(),
            "username", account.username(),
            "roleCode", role.code()));
    log.info(
        "Staff member and account insert executed; commit pending: staffMemberId={}, accountId={}",
        staffMember.id(),
        account.id());
    return StaffMemberSummary.created(staffMember, account);
  }

  /**
   * The temporary password format (printable ASCII, 8 to 72 characters) is validated by the HTTP
   * request. Passwords are never trimmed; the encoder still rejects one longer than bcrypt reads.
   */
  private String hash(String password) {
    if (password == null) throw new IllegalArgumentException("Temporary password is required");
    return passwords.encode(password);
  }
}
