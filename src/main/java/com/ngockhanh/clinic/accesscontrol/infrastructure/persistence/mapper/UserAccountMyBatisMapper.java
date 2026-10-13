package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRoleRecord;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Account writes and existence checks of account creation (UC-005). */
@Mapper
public interface UserAccountMyBatisMapper {
  boolean existsByUsername(@Param("username") String username);

  boolean existsByStaffMember(@Param("staffMemberId") UUID staffMemberId);

  boolean existsByPatient(@Param("patientId") UUID patientId);

  List<UUID> findLinkedPatientIds(@Param("patientIds") Collection<UUID> patientIds);

  /** Inserts the account; returns 0 when its id, username or owner is already used. */
  int insertIfAbsent(AccountRecord account);

  int insertAccountRoles(@Param("grants") List<AccountRoleRecord> grants);
}
