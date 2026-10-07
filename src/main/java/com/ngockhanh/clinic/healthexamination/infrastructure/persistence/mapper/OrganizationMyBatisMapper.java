package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.OrganizationRecord;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationMyBatisMapper {
  OrganizationRecord findById(@Param("id") UUID id);

  boolean existsByTaxCode(@Param("taxCode") String taxCode, @Param("excludedId") UUID excludedId);

  int insert(OrganizationRecord organization);

  int update(
      @Param("organization") OrganizationRecord organization,
      @Param("expectedRowVersion") long expectedRowVersion);

  /**
   * Selects one page of organizations. {@code sortKey} and {@code sortBy} must already be
   * allowlisted by the caller; the XML maps them to fixed ORDER BY clauses.
   */
  List<OrganizationRecord> search(
      @Param("status") String status,
      @Param("pattern") String pattern,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy,
      @Param("limit") int limit,
      @Param("offset") long offset);

  /** Counts organizations with the same predicate as {@link #search}. */
  long count(@Param("status") String status, @Param("pattern") String pattern);
}
