package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Storage port of the health examination agreement aggregate.
 *
 * <p>Every read and write ignores agreements that were soft-deleted.
 */
public interface HealthExaminationAgreementRepository {
  /**
   * Loads an agreement with its services, scoped by organization.
   *
   * @param lock when true the header row is locked until the transaction ends
   */
  Optional<AgreementDetails> findDetails(UUID organizationId, UUID agreementId, boolean lock);

  void insert(HealthExaminationAgreement agreement, UUID createdBy);

  /**
   * Stores a replaced draft configuration: the header, then the service differences.
   *
   * <p>Services that keep their identifier are not recreated. The header row version is incremented
   * exactly once; a service row version is incremented only when that service changed.
   *
   * @param expectedRowVersion header version the caller read
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the header was
   *     changed or deleted since it was read
   */
  void update(HealthExaminationAgreement agreement, long expectedRowVersion);

  /**
   * Stores the soft deletion already applied to the aggregate; nothing is physically removed.
   *
   * @param expectedRowVersion header version the caller read
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the header was
   *     changed or deleted since it was read
   */
  void softDelete(HealthExaminationAgreement agreement, long expectedRowVersion);

  /**
   * Stores the lifecycle status already applied to the aggregate (for example DRAFT to ACTIVE). The
   * configuration is not touched; the header row version is incremented exactly once.
   *
   * @param expectedStatus status the header must still have
   * @param expectedRowVersion header version the caller read
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the header was
   *     changed, deleted or moved to another status since it was read
   */
  void updateStatus(
      HealthExaminationAgreement agreement,
      AgreementStatus expectedStatus,
      long expectedRowVersion);

  /** Returns the subset of the given service line identifiers that a Participant is linked to. */
  Set<UUID> findReferencedAgreementServiceIds(UUID agreementId, Collection<UUID> serviceLineIds);

  /**
   * Returns the highest running number used by an agreement code that starts with the prefix, or 0
   * when none does. Deleted agreements count, because their codes stay reserved.
   *
   * <p>First takes a transaction-scoped lock that serialises code generation, so the caller must
   * insert its agreement in the same transaction. Codes that do not match {@code <prefix><digits>}
   * (for example codes entered by hand in the past) are ignored.
   */
  long highestCodeSequence(String prefix);

  /** Whether the agreement has any Participant, whatever the roster status. */
  boolean hasParticipants(UUID agreementId);

  /** Whether any session was created for this agreement alone, whatever its status. */
  boolean hasSessions(UUID agreementId);

  List<AgreementSummary> findPage(
      UUID organizationId, long offset, int limit, String pattern, String sortKey, String sortBy);

  long count(UUID organizationId, String pattern);

  record AgreementDetails(
      HealthExaminationAgreement agreement, UUID createdBy, Instant createdAt, Instant updatedAt) {}

  record AgreementSummary(
      UUID id,
      String agreementCode,
      String agreementName,
      LocalDate validFrom,
      LocalDate validTo,
      AgreementStatus status,
      Instant createdAt,
      Instant updatedAt,
      long rowVersion) {}
}
