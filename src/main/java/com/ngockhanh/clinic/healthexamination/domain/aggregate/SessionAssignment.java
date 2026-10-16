package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.enums.AssignmentStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.time.Instant;
import java.util.Objects;

/**
 * One Participant in one session, with what happened there. A Participant has any number of
 * assignments over time (sampling one day, examination another, a make-up after a no-show); each
 * keeps its own attendance. A moved or cancelled assignment is history and is never reused.
 */
public class SessionAssignment {
  private final AggregateId id;
  private final AggregateId organizationId;
  private final AggregateId participantId;
  private final AggregateId sessionId;
  private AssignmentStatus status;
  private AggregateId replacedByAssignmentId;
  private Instant attendedAt;
  private String note;
  private AggregateId recordedBy;
  private Instant recordedAt;
  private final Instant createdAt;
  private Instant updatedAt;
  private final long rowVersion;

  private SessionAssignment(
      AggregateId id,
      AggregateId organizationId,
      AggregateId participantId,
      AggregateId sessionId,
      AssignmentStatus status,
      AggregateId replacedByAssignmentId,
      Instant attendedAt,
      String note,
      AggregateId recordedBy,
      Instant recordedAt,
      Instant createdAt,
      Instant updatedAt,
      long rowVersion) {
    if (id == null
        || organizationId == null
        || participantId == null
        || sessionId == null
        || status == null
        || recordedBy == null
        || recordedAt == null
        || createdAt == null
        || updatedAt == null
        || rowVersion < 0
        || (status == AssignmentStatus.ATTENDED) != (attendedAt != null)
        || (status == AssignmentStatus.MOVED) != (replacedByAssignmentId != null))
      throw new IllegalArgumentException("Invalid session assignment");
    this.id = id;
    this.organizationId = organizationId;
    this.participantId = participantId;
    this.sessionId = sessionId;
    this.status = status;
    this.replacedByAssignmentId = replacedByAssignmentId;
    this.attendedAt = attendedAt;
    this.note = note;
    this.recordedBy = recordedBy;
    this.recordedAt = recordedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.rowVersion = rowVersion;
  }

  /** Schedules a Participant into a session. */
  public static SessionAssignment assign(
      AggregateId id,
      AggregateId organizationId,
      AggregateId participantId,
      AggregateId sessionId,
      AggregateId actor,
      Instant now) {
    return new SessionAssignment(
        id,
        organizationId,
        participantId,
        sessionId,
        AssignmentStatus.ASSIGNED,
        null,
        null,
        null,
        actor,
        now,
        now,
        now,
        0);
  }

  public static SessionAssignment restore(
      AggregateId id,
      AggregateId organizationId,
      AggregateId participantId,
      AggregateId sessionId,
      AssignmentStatus status,
      AggregateId replacedByAssignmentId,
      Instant attendedAt,
      String note,
      AggregateId recordedBy,
      Instant recordedAt,
      Instant createdAt,
      Instant updatedAt,
      long rowVersion) {
    return new SessionAssignment(
        id,
        organizationId,
        participantId,
        sessionId,
        status,
        replacedByAssignmentId,
        attendedAt,
        note,
        recordedBy,
        recordedAt,
        createdAt,
        updatedAt,
        rowVersion);
  }

  /**
   * Records what happened in the session: attended, did not show, or back to expected. It can be
   * corrected while the assignment is live or was a no-show; a moved or cancelled assignment is
   * history. Recording the state the assignment already has changes nothing.
   *
   * @param target {@code ATTENDED}, {@code NO_SHOW} or {@code ASSIGNED}
   * @return whether anything changed
   * @throws DomainRuleViolation when the assignment was moved or cancelled
   * @throws IllegalArgumentException when the target is another status or an argument is missing
   */
  public boolean recordAttendance(
      AssignmentStatus target, String note, AggregateId actor, Instant at) {
    if (target == null
        || actor == null
        || at == null
        || (target != AssignmentStatus.ATTENDED
            && target != AssignmentStatus.NO_SHOW
            && target != AssignmentStatus.ASSIGNED))
      throw new IllegalArgumentException("Attendance must be ATTENDED, NO_SHOW or ASSIGNED");
    if (status == AssignmentStatus.MOVED || status == AssignmentStatus.CANCELLED)
      throw new DomainRuleViolation("A moved or cancelled assignment cannot record attendance");
    if (status == target && Objects.equals(this.note, note)) return false;
    boolean keepsAttendedAt =
        status == AssignmentStatus.ATTENDED && target == AssignmentStatus.ATTENDED;
    this.attendedAt =
        target == AssignmentStatus.ATTENDED ? (keepsAttendedAt ? attendedAt : at) : null;
    this.status = target;
    this.note = note;
    touch(actor, at);
    return true;
  }

  /**
   * Marks the assignment as replaced by another one. Only an assignment that is still expected can
   * be moved; an attended assignment is a fact and stays.
   *
   * @throws DomainRuleViolation when the assignment is not {@code ASSIGNED}
   */
  public void moveTo(AggregateId replacement, AggregateId actor, Instant at) {
    if (replacement == null || actor == null || at == null)
      throw new IllegalArgumentException("Replacement, actor and time are required");
    requireExpected("moved");
    this.status = AssignmentStatus.MOVED;
    this.replacedByAssignmentId = replacement;
    touch(actor, at);
  }

  /**
   * Cancels an assignment that is still expected.
   *
   * @throws DomainRuleViolation when the assignment is not {@code ASSIGNED}
   */
  public void cancel(String note, AggregateId actor, Instant at) {
    if (actor == null || at == null)
      throw new IllegalArgumentException("Actor and time are required");
    requireExpected("cancelled");
    this.status = AssignmentStatus.CANCELLED;
    this.note = note;
    touch(actor, at);
  }

  private void requireExpected(String verb) {
    if (status != AssignmentStatus.ASSIGNED)
      throw new DomainRuleViolation("Only an assigned Participant can be " + verb);
  }

  private void touch(AggregateId actor, Instant at) {
    this.recordedBy = actor;
    this.recordedAt = at;
    this.updatedAt = at;
  }

  public AggregateId id() {
    return id;
  }

  public AggregateId organizationId() {
    return organizationId;
  }

  public AggregateId participantId() {
    return participantId;
  }

  public AggregateId sessionId() {
    return sessionId;
  }

  public AssignmentStatus status() {
    return status;
  }

  public AggregateId replacedByAssignmentId() {
    return replacedByAssignmentId;
  }

  public Instant attendedAt() {
    return attendedAt;
  }

  public String note() {
    return note;
  }

  public AggregateId recordedBy() {
    return recordedBy;
  }

  public Instant recordedAt() {
    return recordedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long rowVersion() {
    return rowVersion;
  }
}
