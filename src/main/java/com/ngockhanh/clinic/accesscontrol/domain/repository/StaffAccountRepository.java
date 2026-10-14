package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.UsernameBase;

/** Stores new staff accounts with their role grants. */
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
   * Inserts the account and its role grants.
   *
   * @param account the account to insert
   * @throws IllegalStateException when a row is not inserted
   */
  void save(StaffAccount account);
}
