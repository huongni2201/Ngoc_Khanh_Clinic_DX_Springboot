package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection.AccountRoleRow;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRecord;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StaffAccountMyBatisMapper {
  Integer insert(AccountRecord account);

  /** Takes a transaction-scoped advisory lock named by {@code key}. */
  Integer lockSequence(@Param("key") String key);

  /** Highest number {@code n} among usernames {@code <base><n>}, or 0. */
  Long highestUsernameNumber(@Param("base") String base);

  /** The account with its role, or null. */
  AccountRoleRow findAccountRole(@Param("accountId") UUID accountId);

  /** Active STAFF accounts of active staff members holding the active Administrator role. */
  Long countOtherActiveAdministrators(@Param("excludedAccountId") UUID excludedAccountId);

  /**
   * Replaces the role of an active STAFF account at the expected version; returns the row count.
   */
  Integer changeRole(
      @Param("accountId") UUID accountId,
      @Param("roleId") UUID roleId,
      @Param("expectedRowVersion") Long expectedRowVersion);
}
