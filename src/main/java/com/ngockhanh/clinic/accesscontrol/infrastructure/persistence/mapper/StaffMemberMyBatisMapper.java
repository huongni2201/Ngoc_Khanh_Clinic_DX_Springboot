package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.StaffMemberRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.StaffMemberListView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StaffMemberMyBatisMapper {
  Integer insert(StaffMemberRecord staffMember);

  /** Takes a transaction-scoped advisory lock named by {@code key}. */
  Integer lockSequence(@Param("key") String key);

  /** Highest number {@code n} among staff codes {@code <prefix><n>}, or 0. */
  Long highestStaffCodeNumber(@Param("prefix") String prefix);

  /**
   * Selects one page of staff members with their account. {@code sortKey} and {@code sortBy} must
   * already be allowlisted by the caller; the XML maps them to fixed ORDER BY clauses.
   */
  List<StaffMemberListView> search(
      @Param("pattern") String pattern,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy,
      @Param("limit") Integer limit,
      @Param("offset") Long offset);

  /** Counts staff members with the same predicate as {@link #search}. */
  Long count(@Param("pattern") String pattern);
}
