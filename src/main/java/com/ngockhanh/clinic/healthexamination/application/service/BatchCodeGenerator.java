package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Generates the code of a new health examination batch: {@code KSK-<year>-<running number>}, for
 * example {@code KSK-2026-001}.
 *
 * <p>The year is the current year in the business time zone and the running number restarts at 1
 * each year, with at least three digits. It continues after the highest number already used in
 * that year, deleted batches included, so a code is never reused. The repository serialises the
 * lookup with a transaction-scoped lock, so the caller must insert the batch in the same
 * transaction; the unique constraint on the code stays the last safeguard.
 */
@Component
@RequiredArgsConstructor
public class BatchCodeGenerator {
  static final String PREFIX = "KSK";
  static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final HealthExaminationBatchRepository batches;
  private final Clock clock;

  /** Returns the next free batch code. */
  public String next() {
    int year = LocalDate.now(clock.withZone(BUSINESS_ZONE)).getYear();
    String prefix = PREFIX + "-" + year + "-";
    return String.format("%s%03d", prefix, batches.highestCodeSequence(prefix) + 1);
  }
}
