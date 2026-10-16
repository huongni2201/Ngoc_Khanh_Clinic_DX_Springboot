package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.command.ChangeAccountRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountRoleResponse;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.entity.AccountRole;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the one role of an active staff account (SRS UC-010 Change User Role,
 * docs/api/staff-members.md). The system always keeps at least one account able to administer it
 * (5-EX-02). The change and its audit event are written in one transaction; the user receives the
 * permissions of the new role at the next sign-in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeAccountRoleUseCase {
  static final String CHANGE_ACCOUNT_ROLE = "CHANGE_ACCOUNT_ROLE";
  public static final String ASSIGN_PERMISSION = "USER_ROLE_ASSIGN";

  /** Detail code of the 422 on {@code accountId}: the account is not an active STAFF account. */
  public static final String INACTIVE_ACCOUNT = "InactiveAccount";

  /** Detail code of the 422 on {@code roleCode}: the role is unknown, inactive or PATIENT. */
  public static final String UNAVAILABLE_ROLE = "UnavailableRole";

  /** Detail code of the 409 returned when the last Administrator account would lose its role. */
  public static final String LAST_ADMINISTRATOR = "LastAdministrator";

  private final StaffAccountRepository accounts;
  private final RoleRepository roles;
  private final AuditWriter audit;

  /**
   * Changes the role of the account when the version the caller read is still current.
   *
   * @param accountId account identifier
   * @param command the new role code and the expected account version
   * @param principal authenticated caller, recorded as the audit actor
   * @return the new role and the incremented account version
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code USER_ROLE_ASSIGN}
   * @throws IllegalArgumentException when a value is missing or the role is the current one
   * @throws ResourceNotFoundException when the account does not exist
   * @throws ConcurrentUpdateException when the expected version is stale (5-EX-03)
   * @throws UnprocessableEntityException (HTTP 422) with code {@code InactiveAccount} on {@code
   *     accountId} when the account is not an active STAFF account, or code {@code UnavailableRole}
   *     on {@code roleCode} when the role is unknown, inactive or {@code PATIENT} (5-EX-01)
   * @throws FieldConflictException (HTTP 409, code {@code LastAdministrator}) when no other account
   *     would remain able to administer the system (5-EX-02)
   */
  @Transactional
  public AccountRoleResponse execute(
      UUID accountId, ChangeAccountRoleCommand command, UserPrincipal principal) {
    requirePermission(principal);
    if (accountId == null || command == null)
      throw new IllegalArgumentException("Account ID and command are required");
    if (command.roleCode() == null || command.roleCode().isBlank() || command.rowVersion() == null)
      throw new IllegalArgumentException("Role code and row version are required");

    AccountRole current =
        accounts
            .findAccountRole(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    if (!Boolean.TRUE.equals(current.isActiveStaff()))
      throw new UnprocessableEntityException(
          "accountId", INACTIVE_ACCOUNT, "The account is not an active staff account");
    if (!Objects.equals(command.rowVersion(), current.rowVersion()))
      throw new ConcurrentUpdateException();

    RoleRef role =
        roles
            .findActiveByCode(command.roleCode().trim())
            .filter(StaffAccount::isAssignable)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "roleCode", UNAVAILABLE_ROLE, "The role is not available"));
    if (Boolean.TRUE.equals(current.holds(role.code())))
      throw new IllegalArgumentException("The account already holds the role");

    if (Boolean.TRUE.equals(current.holds(Role.ADMINISTRATOR_CODE))) {
      // Serializes every removal of the Administrator role, so two of them cannot both count the
      // other account as the remaining Administrator.
      accounts.lockAdministratorAccounts();
      if (accounts.countOtherActiveAdministrators(accountId) < 1)
        throw new FieldConflictException(
            "roleCode", LAST_ADMINISTRATOR, "The last active Administrator account keeps its role");
    }

    accounts.changeRole(accountId, role, current.rowVersion());
    Long newRowVersion = current.rowVersion() + 1;

    audit.record(
        principal.userId(),
        CHANGE_ACCOUNT_ROLE,
        "ACCOUNT",
        accountId,
        Map.of("roleCode", current.role().code(), "rowVersion", current.rowVersion()),
        Map.of("roleCode", role.code(), "rowVersion", newRowVersion));
    log.info(
        "Account role change executed; commit pending: accountId={}, roleCode={}",
        accountId,
        role.code());
    return AccountRoleResponse.builder()
        .accountId(accountId)
        .roleCode(role.code())
        .rowVersion(newRowVersion)
        .build();
  }

  /**
   * Requires permission to change account roles. An actor ID alone proves nothing: the principal
   * must be a staff account whose role carries the permission (docs/api/staff-members.md).
   */
  private static void requirePermission(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(ASSIGN_PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
