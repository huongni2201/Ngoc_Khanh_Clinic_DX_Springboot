package com.ngockhanh.clinic.document.application.service;

import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.RenderJobStore;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Deletes the files of finished print jobs once they expire, then marks the jobs {@code EXPIRED}.
 * Files go first: a failure leaves the job as it was and the next run tries again, so a stored file
 * never outlives its row unnoticed.
 */
@Service
public class RenderJobCleanupService {
  private static final Logger log = LoggerFactory.getLogger(RenderJobCleanupService.class);
  private static final int BATCH = 50;

  private final RenderJobStore store;
  private final DocumentStorage storage;
  private final Clock clock;

  public RenderJobCleanupService(RenderJobStore store, DocumentStorage storage, Clock clock) {
    this.store = store;
    this.storage = storage;
    this.clock = clock;
  }

  /**
   * Expires up to one batch of jobs.
   *
   * @return how many jobs were expired
   */
  public int cleanUp() {
    int expired = 0;
    for (var jobId : store.findExpired(clock.instant(), BATCH)) {
      try {
        for (var part : store.findParts(jobId))
          storage.delete(part.storageProvider(), part.storageKey());
        store.deleteParts(jobId);
        store.markExpired(jobId);
        expired++;
      } catch (RuntimeException e) {
        log.warn(
            "Expired print job could not be cleaned: jobId={}, type={}",
            jobId,
            e.getClass().getSimpleName());
      }
    }
    if (expired > 0) log.info("Expired print jobs cleaned: count={}", expired);
    return expired;
  }
}
