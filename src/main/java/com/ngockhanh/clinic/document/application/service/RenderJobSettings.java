package com.ngockhanh.clinic.document.application.service;

import java.time.Duration;

/**
 * Limits of background print jobs.
 *
 * @param maxRecords most records one job may print
 * @param partSize most records in one merged PDF part
 * @param chunkSize records loaded and rendered between two progress saves
 * @param lease how long a worker keeps a job without saving progress
 * @param maxAttempts how many times a job may be taken (a worker that dies counts one)
 * @param retention how long finished files are kept
 * @param maxActivePerRequester jobs one account may have queued or running
 */
public record RenderJobSettings(
    int maxRecords,
    int partSize,
    int chunkSize,
    Duration lease,
    int maxAttempts,
    Duration retention,
    int maxActivePerRequester) {
  public RenderJobSettings {
    if (maxRecords < 1
        || partSize < 1
        || chunkSize < 1
        || maxAttempts < 1
        || maxActivePerRequester < 1
        || lease == null
        || lease.isNegative()
        || lease.isZero()
        || retention == null
        || retention.isNegative()
        || retention.isZero())
      throw new IllegalArgumentException("Invalid render job settings");
  }

  public static RenderJobSettings defaults() {
    return new RenderJobSettings(
        1_000, 200, 25, Duration.ofMinutes(5), 3, Duration.ofHours(24), 3);
  }
}
