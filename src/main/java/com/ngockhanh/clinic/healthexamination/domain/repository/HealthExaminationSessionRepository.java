package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage port of the health examination session aggregate. */
public interface HealthExaminationSessionRepository {
  /**
   * Loads a session scoped by organization.
   *
   * @param lock when true the row is locked until the transaction ends
   */
  Optional<HealthExaminationSession> findById(UUID organizationId, UUID sessionId, boolean lock);

  void insert(HealthExaminationSession session, UUID createdBy);

  /**
   * Stores the new date, activity and site of an open session. The row version is incremented
   * exactly once.
   *
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the session was
   *     changed or cancelled since it was read
   */
  void update(HealthExaminationSession session, long expectedRowVersion);

  /**
   * Stores the status already applied to the aggregate (cancelling an open session).
   *
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the session was
   *     changed or no longer has the expected status
   */
  void updateStatus(
      HealthExaminationSession session, SessionStatus expectedStatus, long expectedRowVersion);

  /**
   * Whether another open session of the organization already has the date, site and activity.
   *
   * @param excludeSessionId the session being edited, or {@code null} on create
   */
  boolean openSlotTaken(
      UUID organizationId,
      LocalDate sessionDate,
      ExaminationSite site,
      SessionActivity activity,
      UUID excludeSessionId);

  /** Reads one session with the number of Participants in it. */
  Optional<SessionSummary> findSummary(UUID organizationId, UUID sessionId);

  List<SessionSummary> findPage(
      SessionCriteria criteria, long offset, int limit, String sortKey, String sortBy);

  long count(SessionCriteria criteria);

  /**
   * List filters; a {@code null} field does not filter.
   *
   * @param agreementId sessions of that agreement and the shared sessions of the organization
   * @param pattern lower-case LIKE pattern matched against the site address
   */
  record SessionCriteria(
      UUID organizationId,
      UUID agreementId,
      LocalDate from,
      LocalDate to,
      SessionActivity activity,
      SessionStatus status,
      String pattern) {}

  /**
   * A session with the counts of its assignments, computed when read.
   *
   * @param assignedCount Participants expected or attended (the people in the session)
   * @param attendedCount Participants who attended
   * @param noShowCount Participants who did not show
   */
  record SessionSummary(
      UUID id,
      UUID organizationId,
      UUID agreementId,
      LocalDate sessionDate,
      SessionActivity activity,
      ExaminationSiteType siteType,
      String siteAddress,
      SessionStatus status,
      long assignedCount,
      long attendedCount,
      long noShowCount,
      Instant createdAt,
      Instant updatedAt,
      long rowVersion) {}
}
