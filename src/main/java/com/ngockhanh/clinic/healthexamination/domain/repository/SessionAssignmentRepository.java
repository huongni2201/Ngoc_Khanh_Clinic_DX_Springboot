package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.SessionAssignment;
import com.ngockhanh.clinic.healthexamination.domain.enums.AssignmentStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Storage port of the Participant-in-session assignments. */
public interface SessionAssignmentRepository {
  /**
   * Loads an assignment of a session, scoped by organization.
   *
   * @param lock when true the row is locked until the transaction ends
   */
  Optional<SessionAssignment> findById(
      UUID organizationId, UUID sessionId, UUID assignmentId, boolean lock);

  /**
   * Returns the subset of the Participants that are already in the session (assigned or attended).
   */
  Set<AggregateId> findLiveParticipantIds(UUID sessionId, Collection<AggregateId> participantIds);

  /**
   * Inserts new assignments. The database unique index on live assignments rejects a Participant
   * that is already in the session.
   *
   * @throws IllegalStateException when fewer rows were written than given
   */
  void insertMany(List<SessionAssignment> assignments);

  /**
   * Stores the state already applied to the aggregate. The row version is incremented exactly once.
   *
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the assignment was
   *     changed since it was read
   */
  void update(SessionAssignment assignment, long expectedRowVersion);

  /**
   * Loads and locks the assignments of a session that have the status, in identifier order so two
   * concurrent callers cannot deadlock.
   */
  List<SessionAssignment> findByStatusForUpdate(
      UUID organizationId, UUID sessionId, AssignmentStatus status);

  /** Number of Participants expected or attended in the session. */
  long countLive(UUID sessionId);

  /** Whether the Participant attended at least one session. */
  boolean hasAttended(AggregateId participantId);

  /**
   * Cancels every assignment of the Participant that is still expected, in one statement, and
   * returns how many were cancelled. Attended and past assignments are untouched.
   */
  int cancelExpectedOfParticipant(AggregateId participantId, AggregateId actor, Instant at);

  /**
   * The examination date of each Participant for a printed form: the earliest session it is still
   * expected at, else the earliest one it attended. Participants with neither are absent.
   */
  Map<AggregateId, LocalDate> findExaminationDates(
      UUID organizationId, Collection<AggregateId> participantIds);

  /** One page of the Participants of a session with their assignment. */
  List<AssignmentRow> findPage(
      UUID organizationId,
      UUID sessionId,
      AssignmentStatus status,
      String pattern,
      long offset,
      int limit,
      String sortKey,
      String sortBy);

  long count(UUID organizationId, UUID sessionId, AssignmentStatus status, String pattern);

  /** Every assignment of a Participant, newest session first, with its session. */
  List<ParticipantAssignment> findByParticipant(UUID organizationId, UUID participantId);

  /**
   * A row of the session's Participant list.
   *
   * @param identificationNumber complete number; the response masks it
   */
  record AssignmentRow(
      UUID assignmentId,
      AssignmentStatus status,
      Instant attendedAt,
      String note,
      long assignmentRowVersion,
      UUID participantId,
      UUID agreementId,
      String participantCode,
      String fullName,
      LocalDate dateOfBirth,
      String sex,
      String identificationNumber,
      String departmentName,
      String positionName,
      RosterStatus rosterStatus) {}

  /** One assignment of a Participant with the session it is for. */
  record ParticipantAssignment(
      UUID assignmentId,
      UUID sessionId,
      LocalDate sessionDate,
      SessionActivity activity,
      ExaminationSiteType siteType,
      String siteAddress,
      SessionStatus sessionStatus,
      AssignmentStatus status,
      UUID replacedByAssignmentId,
      Instant attendedAt,
      String note,
      long rowVersion) {}
}
