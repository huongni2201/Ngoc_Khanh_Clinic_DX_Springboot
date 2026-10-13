package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateUserAccountCommand;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SignInCredentialSender;
import com.ngockhanh.clinic.accesscontrol.application.port.out.SignInCredentialSender.SignInCredentialMessage;
import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.CreateUserAccountResponse;
import com.ngockhanh.clinic.accesscontrol.application.service.UserAccountAccessPolicy;
import com.ngockhanh.clinic.accesscontrol.domain.entity.NewUserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AccountOwner;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AssignableRole;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SmsPhoneNumber;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.TemporaryPasswordPolicy;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Creates a user account for an existing staff member or patient (UC-005).
 *
 * <p>The account, its role grants and the audit event commit together. The sign-in information is
 * sent by SMS only after the commit (ADR-0017), so this use case manages its own transaction
 * instead of joining one: an SMS must never announce an account that is later rolled back, and an
 * SMS failure never undoes the account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateUserAccountUseCase {
  static final String ACTION = "CREATE_USER_ACCOUNT";
  static final String RESOURCE_TYPE = "ACCOUNT";

  /** 409 message contracts, matched by the frontend; see docs/api/user-accounts.md. */
  public static final String USERNAME_TAKEN = "Username already exists";

  public static final String OWNER_LINKED = "Account owner already has an account";
  public static final String OWNER_INACTIVE = "Account owner is not active";
  public static final String OWNER_WITHOUT_MOBILE =
      "Account owner has no valid mobile phone number";
  public static final String ROLE_NOT_ASSIGNABLE = "Role is not assignable";

  /** 400 message contracts. */
  public static final String PASSWORD_POLICY =
      "Temporary password does not meet the password policy";

  public static final String STAFF_ROLE_REQUIRED = "A staff account requires at least one role";
  public static final String PATIENT_ROLES_FORBIDDEN = "A patient account takes no roles";
  public static final String DUPLICATE_ROLE = "A role can be selected only once";

  private final UserAccountAccessPolicy access;
  private final UserAccountRepository accounts;
  private final StaffMemberRepository staffMembers;
  private final PatientAccountOwnerDirectory patients;
  private final RoleRepository roles;
  private final PasswordEncoder passwords;
  private final AuditWriter audit;
  private final SignInCredentialSender sender;
  private final TransactionOperations transactions;
  private final Clock clock;

  /**
   * Creates an ACTIVE account linked to the owner, grants its initial roles and records the
   * creation, then sends the sign-in information to the owner's mobile number.
   *
   * @param command account type, owner, username, temporary password and roles of a staff account
   * @param principal authenticated Administrator
   * @return the created account; {@code smsDelivered} is false when the SMS was not accepted, in
   *     which case the account still exists
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the create permission, or of
   *     type {@code INVALID_INPUT} for a password outside the policy or an invalid role selection
   * @throws ResourceNotFoundException if the owner does not exist
   * @throws ConflictException if the username is taken, the owner is inactive, already has an
   *     account or has no valid mobile number, or a role cannot be granted
   * @throws IllegalArgumentException if a required input is missing or the account type is unknown
   * @throws IllegalStateException if called inside an existing transaction
   */
  public CreateUserAccountResponse execute(
      CreateUserAccountCommand command, UserPrincipal principal) {
    access.requireCreate(principal);
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Account creation must not join an existing transaction");
    if (command == null
        || command.ownerId() == null
        || command.username() == null
        || command.temporaryPassword() == null)
      throw new IllegalArgumentException("Account owner, username and password are required");
    AccountType type =
        AccountType.from(command.accountType())
            .orElseThrow(() -> new IllegalArgumentException("Unknown account type"));
    if (type == AccountType.STAFF && command.roleIds().isEmpty())
      throw invalid(STAFF_ROLE_REQUIRED);
    if (type == AccountType.PATIENT && !command.roleIds().isEmpty())
      throw invalid(PATIENT_ROLES_FORBIDDEN);
    if (new LinkedHashSet<>(command.roleIds()).size() != command.roleIds().size())
      throw invalid(DUPLICATE_ROLE);
    if (!TemporaryPasswordPolicy.isSatisfiedBy(command.temporaryPassword()))
      throw invalid(PASSWORD_POLICY);
    // Hashing is slow by design; it runs before the transaction so no row lock waits on it.
    String passwordHash = encode(command.temporaryPassword());

    Created created =
        transactions.execute(status -> create(type, command, passwordHash, principal.userId()));
    NewUserAccount account = created.account();
    log.info(
        "User account created: accountId={}, accountType={}, roleCount={}",
        account.id(),
        account.type(),
        account.roles().size());

    boolean smsDelivered =
        send(
            new SignInCredentialMessage(
                account.id(),
                created.phone().value(),
                account.username(),
                command.temporaryPassword()));
    return CreateUserAccountResponse.builder()
        .accountId(account.id())
        .accountType(account.type().name())
        .username(account.username())
        .status(account.status().name())
        .ownerId(account.ownerId())
        .roleCodes(account.roleCodes())
        .smsDelivered(smsDelivered)
        .build();
  }

  private Created create(
      AccountType type, CreateUserAccountCommand command, String passwordHash, UUID actor) {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
    UUID accountId = UuidV7Generator.generate();
    AccountOwner owner = lockOwner(type, command.ownerId());
    if (!owner.active()) throw new ConflictException(OWNER_INACTIVE);
    if (accounts.isOwnerLinked(type, owner.id())) throw new ConflictException(OWNER_LINKED);
    SmsPhoneNumber phone =
        SmsPhoneNumber.parse(owner.phone())
            .orElseThrow(() -> new ConflictException(OWNER_WITHOUT_MOBILE));
    if (accounts.existsByUsername(command.username())) throw new ConflictException(USERNAME_TAKEN);

    NewUserAccount account =
        type == AccountType.STAFF
            ? NewUserAccount.forStaff(
                accountId,
                command.username(),
                passwordHash,
                owner.id(),
                staffRoles(command.roleIds()),
                now)
            : NewUserAccount.forPatient(
                accountId, command.username(), passwordHash, owner.id(), patientRole(), now);
    if (!accounts.insertIfAbsent(account)) {
      // A concurrent request took the username or the owner after the checks above.
      if (accounts.existsByUsername(account.username()))
        throw new ConflictException(USERNAME_TAKEN);
      if (accounts.isOwnerLinked(type, owner.id())) throw new ConflictException(OWNER_LINKED);
      throw new IllegalStateException("Account was not inserted");
    }
    accounts.grantRoles(
        account.id(), account.roles().stream().map(AssignableRole::id).toList(), actor, now);
    audit.record(actor, ACTION, RESOURCE_TYPE, account.id(), null, auditSnapshot(account));
    log.info("User account insert executed; commit pending: accountId={}", account.id());
    return new Created(account, phone);
  }

  private AccountOwner lockOwner(AccountType type, UUID ownerId) {
    if (type == AccountType.STAFF)
      return staffMembers
          .lockForAccountLink(ownerId)
          .orElseThrow(() -> new ResourceNotFoundException("Account owner"));
    return patients
        .lockForAccountLink(ownerId)
        .map(patient -> new AccountOwner(patient.id(), type, patient.active(), patient.phone()))
        .orElseThrow(() -> new ResourceNotFoundException("Account owner"));
  }

  private List<AssignableRole> staffRoles(List<UUID> roleIds) {
    Map<UUID, AssignableRole> found =
        roles.lockByIds(Set.copyOf(roleIds)).stream()
            .collect(Collectors.toMap(AssignableRole::id, Function.identity()));
    List<AssignableRole> granted = new ArrayList<>();
    for (UUID roleId : roleIds) {
      AssignableRole role = found.get(roleId);
      if (role == null || !role.active() || role.isPatientRole())
        throw new ConflictException(ROLE_NOT_ASSIGNABLE);
      granted.add(role);
    }
    return granted;
  }

  private AssignableRole patientRole() {
    return roles
        .findByCode(AssignableRole.PATIENT_ROLE_CODE)
        .filter(AssignableRole::active)
        .orElseThrow(() -> new IllegalStateException("The active PATIENT role is not seeded"));
  }

  private String encode(String password) {
    try {
      return passwords.encode(password);
    } catch (IllegalArgumentException tooLong) {
      // UserPasswordEncoder rejects passwords above 72 UTF-8 bytes after normalization.
      throw invalid(PASSWORD_POLICY);
    }
  }

  private boolean send(SignInCredentialMessage message) {
    try {
      return sender.send(message);
    } catch (RuntimeException failure) {
      log.warn(
          "Sign-in credential SMS failed: accountId={}, errorType={}",
          message.accountId(),
          failure.getClass().getSimpleName());
      return false;
    }
  }

  private static Map<String, Object> auditSnapshot(NewUserAccount account) {
    // LinkedHashMap keeps the null owner field of the other account type.
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("accountType", account.type().name());
    snapshot.put("staffMemberId", account.staffMemberId());
    snapshot.put("patientId", account.patientId());
    snapshot.put("status", account.status().name());
    snapshot.put("roleCodes", account.roleCodes());
    return snapshot;
  }

  private static ApplicationException invalid(String message) {
    return new ApplicationException(ApplicationException.Type.INVALID_INPUT, message);
  }

  private record Created(NewUserAccount account, SmsPhoneNumber phone) {}
}
