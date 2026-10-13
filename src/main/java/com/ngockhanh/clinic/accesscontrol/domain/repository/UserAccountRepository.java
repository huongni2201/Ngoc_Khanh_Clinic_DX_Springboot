package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.entity.NewUserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.entity.UserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Accounts for sign-in and for account creation by an Administrator (UC-005). */
public interface UserAccountRepository {
  /**
   * @param username sign-in identifier, compared exactly
   * @return the account with its active role grants, or empty
   */
  Optional<UserAccount> findByUsername(String username);

  /**
   * @param username sign-in identifier, compared exactly
   * @return true when an account already uses the username
   */
  boolean existsByUsername(String username);

  /**
   * @param type owner kind
   * @param ownerId staff member or patient identifier
   * @return true when the owner is already linked to an account
   */
  boolean isOwnerLinked(AccountType type, UUID ownerId);

  /**
   * @param patientIds patients to check
   * @return the subset already linked to an account
   */
  Set<UUID> findLinkedPatientIds(Collection<UUID> patientIds);

  /**
   * Inserts the account unless its id, username or owner is already used.
   *
   * @param account account to insert, without its roles
   * @return true when inserted; false when a unique identity is already taken
   */
  boolean insertIfAbsent(NewUserAccount account);

  /**
   * Grants roles to an account.
   *
   * @param accountId account receiving the roles
   * @param roleIds roles to grant
   * @param grantedBy STAFF account granting the roles
   * @param grantedAt grant time
   * @throws IllegalStateException if not every role is granted
   */
  void grantRoles(UUID accountId, List<UUID> roleIds, UUID grantedBy, Instant grantedAt);
}
