package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AccountOwner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Staff member profiles as account owners. */
public interface StaffMemberRepository {
  /**
   * Reads a staff member and holds a share lock on it until the end of the transaction.
   *
   * @param staffMemberId staff member identifier
   * @return the staff member as an account owner, or empty
   */
  Optional<AccountOwner> lockForAccountLink(UUID staffMemberId);

  /**
   * Finds active staff members without an account whose staff code, full name or phone number
   * contains the keyword. LIKE wildcards in the keyword are matched literally.
   *
   * @param keyword search text
   * @param limit maximum number of rows
   * @return candidates ordered by full name
   */
  List<StaffOwnerCandidate> searchActiveWithoutAccount(String keyword, int limit);

  /**
   * Staff member offered as an account owner.
   *
   * @param id staff member identifier
   * @param staffCode staff code
   * @param fullName full name
   * @param departmentName primary department name, or null
   * @param phone phone number as stored, or null
   * @param email email, or null
   */
  record StaffOwnerCandidate(
      UUID id,
      String staffCode,
      String fullName,
      String departmentName,
      String phone,
      String email) {}
}
