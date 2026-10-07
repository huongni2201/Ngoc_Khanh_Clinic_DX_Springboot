package com.ngockhanh.clinic.audit.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Published recording contract for business, authentication and session audit events. */
public interface AuditWriter {
  /**
   * Appends an event in the caller's transaction; authorization remains the caller's
   * responsibility.
   *
   * @param actor authenticated account performing the mutation
   * @param action business action recorded
   * @param entityType type of the affected resource
   * @param entityId identifier of the affected resource
   * @param beforeSnapshot concise safe metadata before the mutation, or null for an empty snapshot
   * @param afterSnapshot concise safe metadata after the mutation, or null for an empty snapshot
   * @throws IllegalArgumentException if the actor is missing
   * @throws IllegalStateException if the event is not inserted exactly once
   */
  void record(
      UUID actor,
      String action,
      String entityType,
      UUID entityId,
      Object beforeSnapshot,
      Object afterSnapshot);

  /**
   * Appends an account event with empty metadata in the caller's transaction.
   *
   * @param userId authenticated account performing the action and identifying the affected resource
   * @param action authentication or session action recorded
   * @param occurredAt required time when the action occurred
   * @param correlationId request correlation identifier, or null when unavailable
   * @throws IllegalArgumentException if the account or occurrence time is missing
   * @throws IllegalStateException if the event is not inserted exactly once
   */
  void record(UUID userId, String action, Instant occurredAt, UUID correlationId);
}
