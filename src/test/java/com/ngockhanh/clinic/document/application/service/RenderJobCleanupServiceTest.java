package com.ngockhanh.clinic.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.InMemoryStorage;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.InMemoryStore;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenderJobCleanupServiceTest {
  private static final Instant START = Instant.parse("2026-10-13T08:00:00Z");

  private final MutableClock clock = new MutableClock(START);
  private final InMemoryStore store = new InMemoryStore();
  private final InMemoryStorage storage = new InMemoryStorage();
  private final RenderJobCleanupService cleanup = new RenderJobCleanupService(store, storage, clock);

  /** A finished job with one stored part that expires after the given time. */
  private UUID finishedJob(boolean succeeded, Duration expiresIn) {
    UUID id = UUID.randomUUID();
    store.insert(id, "T", UUID.randomUUID(), UUID.randomUUID(), Map.of(), List.of(UUID.randomUUID()), START);
    var claimed = store.claimNext(START, START.plus(Duration.ofMinutes(5)), 3);
    // claimNext takes the oldest waiting job; make sure it is this one
    assertThat(claimed.orElseThrow().id()).isEqualTo(id);
    String key = "render-jobs/2026/10/" + id + "-1.pdf";
    storage.files.put("LOCAL:" + key, new byte[] {1});
    store.addPart(
        id,
        claimed.get().lockToken(),
        1,
        1,
        new NewFile(UUID.randomUUID(), "LOCAL", key, "part-1.pdf", "application/pdf", 1, new byte[32]),
        START.plus(Duration.ofMinutes(5)));
    store.finish(
        id, claimed.get().lockToken(), succeeded, succeeded ? null : "X", 1, List.of(), START, START.plus(expiresIn));
    return id;
  }

  @Test
  void deletesTheFilesOfExpiredJobsThenMarksThemExpired() {
    var expired = finishedJob(true, Duration.ofHours(24));
    var failed = finishedJob(false, Duration.ofHours(24));
    var fresh = finishedJob(true, Duration.ofHours(48));
    clock.advance(Duration.ofHours(25));

    assertThat(cleanup.cleanUp()).isEqualTo(2);

    assertThat(store.row(expired).status).isEqualTo("EXPIRED");
    assertThat(store.row(failed).status).isEqualTo("EXPIRED");
    assertThat(store.row(expired).parts).isEmpty();
    assertThat(store.row(fresh).status).isEqualTo("SUCCEEDED");
    assertThat(store.row(fresh).parts).hasSize(1);
    assertThat(storage.files).hasSize(1);
    assertThat(storage.deleted).hasSize(2);
  }

  @Test
  void doesNothingBeforeTheExpiry() {
    var job = finishedJob(true, Duration.ofHours(24));
    clock.advance(Duration.ofHours(23));

    assertThat(cleanup.cleanUp()).isZero();
    assertThat(store.row(job).status).isEqualTo("SUCCEEDED");
    assertThat(storage.deleted).isEmpty();
  }

  @Test
  void aFileThatCannotBeDeletedLeavesTheJobForTheNextRunAndOthersGoOn() {
    var stuck = finishedJob(true, Duration.ofHours(24));
    var other = finishedJob(true, Duration.ofHours(24));
    clock.advance(Duration.ofHours(25));
    var failing =
        new InMemoryStorage() {
          @Override
          public void delete(String provider, String key) {
            if (key.contains(stuck.toString())) throw new IllegalStateException("locked");
            super.delete(provider, key);
          }
        };
    failing.files.putAll(storage.files);
    var service = new RenderJobCleanupService(store, failing, clock);

    assertThat(service.cleanUp()).isEqualTo(1);

    assertThat(store.row(stuck).status).isEqualTo("SUCCEEDED");
    assertThat(store.row(stuck).parts).hasSize(1);
    assertThat(store.row(other).status).isEqualTo("EXPIRED");
    assertThat(new RenderJobCleanupService(store, storage, clock).cleanUp()).isEqualTo(1);
    assertThat(store.row(stuck).status).isEqualTo("EXPIRED");
  }
}
