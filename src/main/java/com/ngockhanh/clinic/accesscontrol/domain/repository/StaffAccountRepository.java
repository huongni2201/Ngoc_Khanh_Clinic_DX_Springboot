package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.entity.AccountRole;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.UsernameBase;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.Optional;
import java.util.UUID;

/** Stores staff accounts and changes the role they hold. */
public interface StaffAccountRepository {
  /**
   * Allocates the next free username: the base followed by the next number, starting at 1. Holds a
   * transaction-scoped lock on the base, so it must run in the transaction that saves the account.
   *
   * @param base username prefix
   * @return for example {@code annv1}, or {@code annv2} when {@code annv1} exists
   */
  String nextUsername(UsernameBase base);

  /**
   * Inserts the account with its role.
   *
   * @param account the account to insert
   * @throws IllegalStateException when the row is not inserted
   */
  void save(StaffAccount account);

  /**
   * Finds the role an account holds, with the account state needed to change it.
   *
   * @param accountId account identifier
   * @return the account role, or empty when no account has this id
   */
  Optional<AccountRole> findAccountRole(UUID accountId);

  /**
   * Holds a transaction-scoped lock shared by every change that may remove the Administrator role
   * from an account, so two of them cannot both count the other Administrator as still present.
   */
  void lockAdministratorAccounts();

  /**
   * Counts the accounts, other than the given one, that can still administer the system: active
   * STAFF accounts of active staff members holding the active {@code ADMINISTRATOR} role.
   *
   * @param excludedAccountId account left out of the count
   * @return the number of such accounts
   */
  Long countOtherActiveAdministrators(UUID excludedAccountId);

  /**
   * Replaces the role of an active STAFF account and increments its row version.
   *
   * @param accountId account identifier
   * @param role the new role
   * @param expectedRowVersion version the caller read
   * @throws ConcurrentUpdateException when the account changed since it was read or is no longer an
   *     active STAFF account
   */
  void changeRole(UUID accountId, RoleRef role, Long expectedRowVersion);
}
