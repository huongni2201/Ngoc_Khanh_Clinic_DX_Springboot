package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffMember;

/** Stores staff members and allocates their codes. */
public interface StaffMemberRepository {
  /**
   * Allocates the next staff code {@code NV-<n>}, {@code n} zero-padded to 4 digits. Holds a
   * transaction-scoped lock, so it must run in the transaction that saves the staff member.
   *
   * @return a code no other committed staff member uses
   */
  String nextStaffCode();

  /**
   * Inserts a new staff member.
   *
   * @param staffMember the staff member to insert
   * @throws IllegalStateException when the row is not inserted
   */
  void save(StaffMember staffMember);
}
