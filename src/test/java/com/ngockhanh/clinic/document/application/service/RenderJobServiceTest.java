package com.ngockhanh.clinic.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.document.application.rendering.RenderJobs.NewRenderJob;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.FakeSource;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.InMemoryStorage;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.InMemoryStore;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.MutableClock;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenderJobServiceTest {
  private static final String TYPE = "TEST_FORM";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T08:00:00Z"));
  private final InMemoryStore store = new InMemoryStore();
  private final InMemoryStorage storage = new InMemoryStorage();
  private final RenderJobSettings settings =
      new RenderJobSettings(5, 2, 3, Duration.ofMinutes(5), 3, Duration.ofHours(24), 3);
  private final RenderJobService service =
      new RenderJobService(store, storage, List.of(new FakeSource(TYPE)), settings, clock);
  private final UUID requester = UUID.randomUUID();

  private static List<UUID> ids(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(UUID.randomUUID());
    return ids;
  }

  private NewRenderJob request(UUID key, List<UUID> ids) {
    return new NewRenderJob(TYPE, requester, key, Map.of("batchId", "b1"), ids);
  }

  @Test
  void queuesAJobThatHoldsIdentifiersOnly() {
    var ids = ids(3);

    var queued = service.enqueue(request(UUID.randomUUID(), ids));

    assertThat(queued.replayed()).isFalse();
    var row = store.row(queued.jobId());
    assertThat(row.status).isEqualTo("QUEUED");
    assertThat(row.documentType).isEqualTo(TYPE);
    assertThat(row.requestedBy).isEqualTo(requester);
    assertThat(row.recordIds).containsExactlyElementsOf(ids);
    assertThat(row.context).isEqualTo(Map.of("batchId", "b1"));
    assertThat(row.createdAt).isEqualTo(clock.instant());
  }

  @Test
  void aRetryWithTheSameKeyAndRequestReturnsTheSameJob() {
    var key = UUID.randomUUID();
    var ids = ids(2);

    var first = service.enqueue(request(key, ids));
    var again = service.enqueue(request(key, ids));

    assertThat(again.jobId()).isEqualTo(first.jobId());
    assertThat(again.replayed()).isTrue();
    assertThat(store.rows).hasSize(1);
  }

  @Test
  void theSameKeyForADifferentRequestIsRefused() {
    var key = UUID.randomUUID();
    service.enqueue(request(key, ids(2)));

    assertThatThrownBy(() -> service.enqueue(request(key, ids(2))))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Idempotency-Key");
    assertThatThrownBy(
            () ->
                service.enqueue(
                    new NewRenderJob(TYPE, requester, key, Map.of("batchId", "other"), ids(2))))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void keysAreScopedToTheRequester() {
    var key = UUID.randomUUID();
    var ids = ids(1);
    var mine = service.enqueue(request(key, ids));
    var theirs =
        service.enqueue(new NewRenderJob(TYPE, UUID.randomUUID(), key, Map.of("batchId", "b1"), ids));

    assertThat(theirs.jobId()).isNotEqualTo(mine.jobId());
    assertThat(theirs.replayed()).isFalse();
  }

  @Test
  void aConcurrentRetryThatWinsTheInsertIsReturnedAsAReplay() {
    var key = UUID.randomUUID();
    var ids = ids(2);
    var winner = UUID.randomUUID();
    store.insert(winner, TYPE, requester, key, Map.of("batchId", "b1"), ids, clock.instant());
    var racing =
        new InMemoryStore() {
          private boolean first = true;

          @Override
          public Optional<Job> findByIdempotencyKey(UUID requestedBy, UUID idempotencyKey) {
            if (first) {
              first = false;
              return Optional.empty();
            }
            return store.findByIdempotencyKey(requestedBy, idempotencyKey);
          }

          @Override
          public boolean insert(
              UUID id,
              String documentType,
              UUID requestedBy,
              UUID idempotencyKey,
              Map<String, String> context,
              List<UUID> recordIds,
              Instant now) {
            return false;
          }
        };
    var racingService =
        new RenderJobService(racing, storage, List.of(new FakeSource(TYPE)), settings, clock);

    var queued = racingService.enqueue(request(key, ids));

    assertThat(queued.jobId()).isEqualTo(winner);
    assertThat(queued.replayed()).isTrue();
  }

  @Test
  void refusesAMalformedRequest() {
    var key = UUID.randomUUID();
    var duplicated = ids(2);
    duplicated.add(duplicated.get(0));

    assertThatThrownBy(() -> service.enqueue(new NewRenderJob("OTHER", requester, key, Map.of(), ids(1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.enqueue(new NewRenderJob(null, requester, key, Map.of(), ids(1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.enqueue(new NewRenderJob(TYPE, null, key, Map.of(), ids(1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.enqueue(new NewRenderJob(TYPE, requester, null, Map.of(), ids(1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.enqueue(request(key, List.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.enqueue(request(key, ids(6))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("5");
    assertThatThrownBy(() -> service.enqueue(request(key, duplicated)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.enqueue(request(key, Arrays.asList(UUID.randomUUID(), null))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(store.rows).isEmpty();
  }

  @Test
  void acceptsTheMostRecordsAJobMayHold() {
    assertThat(service.enqueue(request(UUID.randomUUID(), ids(5))).jobId()).isNotNull();
  }

  @Test
  void needsWritableStorageBecauseTheOutputHasToBeKept() {
    storage.writable = false;

    assertThatThrownBy(() -> service.enqueue(request(UUID.randomUUID(), ids(1))))
        .isInstanceOf(DependencyUnavailableException.class);
    assertThat(store.rows).isEmpty();
  }

  @Test
  void limitsHowManyJobsOneAccountMayHaveWaitingOrRunning() {
    for (int i = 0; i < 3; i++) service.enqueue(request(UUID.randomUUID(), ids(1)));

    assertThatThrownBy(() -> service.enqueue(request(UUID.randomUUID(), ids(1))))
        .isInstanceOf(ConflictException.class);

    var first = store.rows.values().iterator().next();
    first.status = "SUCCEEDED";
    assertThat(service.enqueue(request(UUID.randomUUID(), ids(1))).jobId()).isNotNull();
    // someone else is not affected
    assertThat(
            service
                .enqueue(new NewRenderJob(TYPE, UUID.randomUUID(), UUID.randomUUID(), Map.of(), ids(1)))
                .jobId())
        .isNotNull();
  }

  @Test
  void aReplayStillWorksWhenTheAccountIsAtItsLimit() {
    var key = UUID.randomUUID();
    var ids = ids(1);
    var first = service.enqueue(request(key, ids));
    service.enqueue(request(UUID.randomUUID(), ids(1)));
    service.enqueue(request(UUID.randomUUID(), ids(1)));

    assertThat(service.enqueue(request(key, ids)).jobId()).isEqualTo(first.jobId());
  }
}
