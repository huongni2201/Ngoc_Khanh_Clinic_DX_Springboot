package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import java.util.List;
import java.util.Set;

/** Looks up roles for grants. */
public interface RoleRepository {
  /**
   * Finds the active roles among the codes. Unknown or inactive codes are absent from the result.
   *
   * @param codes role codes, compared exactly
   * @return matching active roles ordered by code
   */
  List<RoleRef> findActiveByCodes(Set<String> codes);
}
