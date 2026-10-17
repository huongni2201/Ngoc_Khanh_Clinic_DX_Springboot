package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import java.time.LocalDate;

/**
 * One scheduled occasion of a corporate examination: a date, a site and an activity. It holds no
 * price, no service and no capacity; staff decide how many people go in it, and the number of
 * assigned Participants is only counted when the session is read.
 *
 * <p>A session may belong to one agreement or be shared by the agreements of the organization.
 */
public class HealthExaminationSession {
  private final AggregateId id;
  private final AggregateId organizationId;
  private final AggregateId agreementId;
  private LocalDate sessionDate;
  private SessionActivity activity;
  private ExaminationSite site;
  private SessionStatus status;
  private final long rowVersion;

  private HealthExaminationSession(
      AggregateId id,
      AggregateId organizationId,
      AggregateId agreementId,
      LocalDate sessionDate,
      SessionActivity activity,
      ExaminationSite site,
      SessionStatus status,
      long rowVersion) {
    if (id == null
        || organizationId == null
        || sessionDate == null
        || activity == null
        || site == null
        || status == null
        || rowVersion < 0) throw new IllegalArgumentException("Invalid session");
    this.id = id;
    this.organizationId = organizationId;
    this.agreementId = agreementId;
    this.sessionDate = sessionDate;
    this.activity = activity;
    this.site = site;
    this.status = status;
    this.rowVersion = rowVersion;
  }

  /**
   * Creates an open session.
   *
   * @param agreementId the only agreement the session serves, or {@code null} when every agreement
   *     of the organization may use it
   */
  public static HealthExaminationSession open(
      AggregateId id,
      AggregateId organizationId,
      AggregateId agreementId,
      LocalDate sessionDate,
      SessionActivity activity,
      ExaminationSite site) {
    return new HealthExaminationSession(
        id, organizationId, agreementId, sessionDate, activity, site, SessionStatus.OPEN, 0);
  }

  public static HealthExaminationSession restore(
      AggregateId id,
      AggregateId organizationId,
      AggregateId agreementId,
      LocalDate sessionDate,
      SessionActivity activity,
      ExaminationSite site,
      SessionStatus status,
      long rowVersion) {
    return new HealthExaminationSession(
        id, organizationId, agreementId, sessionDate, activity, site, status, rowVersion);
  }

  /**
   * Changes the date, the activity and the site of an open session.
   *
   * @throws DomainRuleViolation when the session is cancelled
   * @throws IllegalArgumentException when a value is missing
   */
  public void reschedule(LocalDate sessionDate, SessionActivity activity, ExaminationSite site) {
    requireOpen();
    if (sessionDate == null || activity == null || site == null)
      throw new IllegalArgumentException("Session date, activity and site are required");
    this.sessionDate = sessionDate;
    this.activity = activity;
    this.site = site;
  }

  /**
   * Cancels an open session. The caller checks that nobody is still assigned to it.
   *
   * @throws DomainRuleViolation when the session is already cancelled
   */
  public void cancel() {
    requireOpen();
    status = SessionStatus.CANCELLED;
  }

  /**
   * Checks that the session still accepts assignments and changes.
   *
   * @throws DomainRuleViolation when the session is cancelled
   */
  public void requireOpen() {
    if (status != SessionStatus.OPEN) throw new DomainRuleViolation("Session is cancelled");
  }

  /**
   * Checks that a Participant of the given agreement may be scheduled here: a session that serves
   * one agreement takes only that agreement's Participants.
   *
   * @throws DomainRuleViolation otherwise
   */
  public void requireServes(AggregateId participantAgreementId) {
    if (agreementId != null && !agreementId.equals(participantAgreementId))
      throw new DomainRuleViolation("Session belongs to another agreement");
  }

  public AggregateId id() {
    return id;
  }

  public AggregateId organizationId() {
    return organizationId;
  }

  /** The only agreement the session serves, or {@code null} when it is shared. */
  public AggregateId agreementId() {
    return agreementId;
  }

  public LocalDate sessionDate() {
    return sessionDate;
  }

  public SessionActivity activity() {
    return activity;
  }

  public ExaminationSite site() {
    return site;
  }

  public SessionStatus status() {
    return status;
  }

  public long rowVersion() {
    return rowVersion;
  }
}
