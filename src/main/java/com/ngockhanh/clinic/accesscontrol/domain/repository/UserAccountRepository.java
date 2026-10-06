package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.entity.UserAccount;
import java.util.Optional;

/** Loads accounts for authentication. */
public interface UserAccountRepository {
  /**
   * Finds an account by its exact, case-sensitive username, with its active role grants.
   *
   * @param username username exactly as submitted
   * @return the account, or empty when no account has this username
   */
  Optional<UserAccount> findByUsername(String username);
}
