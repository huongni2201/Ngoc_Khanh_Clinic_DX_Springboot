package com.ngockhanh.clinic.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.ConcatMerger;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.FakeRenderer;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.FakeSource;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.InMemoryStorage;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.InMemoryStore;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes.MutableClock;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenderJobProcessorTest {
  private static final String TYPE = "TEST_FORM";
  private static final Instant START = Instant.parse("2026-10-13T08:00:00Z");

  private final MutableClock clock = new MutableClock(START);
  private final InMemoryStore store = new InMemoryStore();
  private final FakeRenderer renderer = new FakeRenderer();
  private final InMemoryStorage storage = new InMemoryStorage();
  private final ConcatMerger merger = new ConcatMerger();
  private final FakeSource source = new FakeSource(TYPE);
  private final RenderJobSettings settings =
      new RenderJobSettings(1000, 2, 3, Duration.ofMinutes(5), 3, Duration.ofHours(24), 3);
  private final RenderJobProcessor processor =
      new RenderJobProcessor(store, renderer, storage, merger, List.of(source), settings, clock);
  private final UUID requester = UUID.randomUUID();

  private static String tag(UUID id) {
    return "[" + id.toString().substring(0, 8) + "]";
  }

  private static String shortId(UUID id) {
    return id.toString().substring(0, 8);
  }

  private List<UUID> ids(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(UUID.randomUUID());
    return ids;
  }

  private UUID queue(String type, List<UUID> ids) {
    UUID id = UUID.randomUUID();
    store.insert(id, type, requester, UUID.randomUUID(), Map.of("k", "v"), ids, clock.instant());
    return id;
  }

  private String stored(InMemoryStore.Row row, int partNo) {
    var part = row.parts.get(partNo);
    return new String(storage.files.get(part.storageProvider() + ":" + part.storageKey()), StandardCharsets.UTF_8);
  }

  @Test
  void returnsFalseWhenNothingIsWaiting() {
    assertThat(processor.processNext()).isFalse();
  }

  @Test
  void printsEveryRecordIntoPartsAndPinsOneTemplateVersion() {
    var ids = ids(5);
    var job = queue(TYPE, ids);

    assertThat(processor.processNext()).isTrue();

    var row = store.row(job);
    assertThat(row.status).isEqualTo("SUCCEEDED");
    assertThat(row.processed).isEqualTo(5);
    assertThat(row.skipped).isEmpty();
    assertThat(row.templateVersionId).isEqualTo(renderer.versionId);
    assertThat(renderer.opened).isEqualTo(1);
    assertThat(row.finishedAt).isEqualTo(START);
    assertThat(row.expiresAt).isEqualTo(START.plus(Duration.ofHours(24)));
    assertThat(row.lockToken).isNull();
    // chunks of 3 records, parts of 2 records: the last single-record part is not merged
    assertThat(source.chunks).hasSize(2);
    assertThat(source.chunks.get(0)).hasSize(3);
    assertThat(source.chunks.get(1)).hasSize(2);
    assertThat(row.parts).hasSize(3);
    assertThat(stored(row, 1)).isEqualTo(tag(ids.get(0)) + tag(ids.get(1)));
    assertThat(stored(row, 2)).isEqualTo(tag(ids.get(2)) + tag(ids.get(3)));
    assertThat(stored(row, 3)).isEqualTo(tag(ids.get(4)));
    assertThat(row.parts.get(1).recordCount()).isEqualTo(2);
    assertThat(row.parts.get(3).recordCount()).isEqualTo(1);
    assertThat(merger.calls).containsExactly(2, 2);
    assertThat(row.parts.get(1).storageKey()).isEqualTo("render-jobs/2026/10/" + job + "-1.pdf");
  }

  @Test
  void leavesOutRecordsTheSourceSkipsButCountsThemAsHandled() {
    var ids = ids(4);
    source.skip.put(ids.get(1), "CANCELLED");
    var job = queue(TYPE, ids);

    processor.processNext();

    var row = store.row(job);
    assertThat(row.status).isEqualTo("SUCCEEDED");
    assertThat(row.processed).isEqualTo(4);
    assertThat(row.skipped).containsExactly(new DocumentValuesSource.Skipped(ids.get(1), "CANCELLED"));
    assertThat(stored(row, 1)).isEqualTo(tag(ids.get(0)) + tag(ids.get(2)));
    assertThat(stored(row, 2)).isEqualTo(tag(ids.get(3)));
  }

  @Test
  void skipsARecordThatDoesNotFitItsPagesAndOneThatFailsToRender() {
    var ids = ids(4);
    renderer.failures.put(shortId(ids.get(0)), new ConflictException("does not fit"));
    renderer.failures.put(shortId(ids.get(2)), new IllegalStateException("broken"));
    var job = queue(TYPE, ids);

    processor.processNext();

    var row = store.row(job);
    assertThat(row.status).isEqualTo("SUCCEEDED");
    assertThat(row.skipped)
        .containsExactly(
            new DocumentValuesSource.Skipped(ids.get(0), RenderJobProcessor.TOO_LONG),
            new DocumentValuesSource.Skipped(ids.get(2), RenderJobProcessor.RENDER_FAILED));
    assertThat(stored(row, 1)).isEqualTo(tag(ids.get(1)) + tag(ids.get(3)));
  }

  @Test
  void aConverterThatKeepsFailingFailsTheJobButASingleOutageDoesNot() {
    var down = new DependencyUnavailableException("down", null);
    var ids = ids(5);
    renderer.failures.put(shortId(ids.get(0)), down);
    renderer.failures.put(shortId(ids.get(2)), down);
    var tolerated = queue(TYPE, ids);
    processor.processNext();
    assertThat(store.row(tolerated).status).isEqualTo("SUCCEEDED");
    assertThat(store.row(tolerated).skipped).hasSize(2);

    var all = ids(5);
    for (var id : all) renderer.failures.put(shortId(id), down);
    var failing = queue(TYPE, all);
    processor.processNext();
    var row = store.row(failing);
    assertThat(row.status).isEqualTo("FAILED");
    assertThat(row.errorCode).isEqualTo("RENDERER_UNAVAILABLE");
    assertThat(row.skipped).hasSize(2);
  }

  @Test
  void failsWhenNothingCouldBePrinted() {
    var ids = ids(2);
    for (var id : ids) source.skip.put(id, "NOT_FOUND");
    var job = queue(TYPE, ids);

    processor.processNext();

    assertThat(store.row(job).status).isEqualTo("FAILED");
    assertThat(store.row(job).errorCode).isEqualTo("NOTHING_TO_PRINT");
    assertThat(store.row(job).skipped).hasSize(2);
    assertThat(storage.files).isEmpty();
  }

  @Test
  void failsWithAClearCodeWhenThereIsNoUsableTemplate() {
    renderer.openFailure = new ResourceNotFoundException("Template");
    var missing = queue(TYPE, ids(1));
    processor.processNext();
    assertThat(store.row(missing).errorCode).isEqualTo("NO_TEMPLATE");

    renderer.openFailure = new IllegalStateException("stored file is corrupt");
    var broken = queue(TYPE, ids(1));
    processor.processNext();
    assertThat(store.row(broken).errorCode).isEqualTo("TEMPLATE_UNAVAILABLE");
    assertThat(store.row(broken).status).isEqualTo("FAILED");
  }

  @Test
  void failsForADocumentTypeNoModuleServes() {
    var job = queue("OTHER", ids(1));
    processor.processNext();
    assertThat(store.row(job).status).isEqualTo("FAILED");
    assertThat(store.row(job).errorCode).isEqualTo("UNSUPPORTED_DOCUMENT_TYPE");
  }

  @Test
  void failsWhenTheOwningModuleSaysTheRecordsCannotBePrintedAnymore() {
    source.failure = new ConflictException("batch closed");
    var closed = queue(TYPE, ids(1));
    processor.processNext();
    assertThat(store.row(closed).errorCode).isEqualTo("SOURCE_NOT_PRINTABLE");

    source.failure = new ResourceNotFoundException("Batch");
    var gone = queue(TYPE, ids(1));
    processor.processNext();
    assertThat(store.row(gone).errorCode).isEqualTo("SOURCE_NOT_FOUND");
  }

  @Test
  void aStorageOrMergeFaultFailsTheJob() {
    storage.failWrites = true;
    var disk = queue(TYPE, ids(2));
    processor.processNext();
    assertThat(store.row(disk).errorCode).isEqualTo("STORAGE_FAILED");
    assertThat(store.row(disk).parts).isEmpty();

    storage.failWrites = false;
    merger.failure = new DependencyUnavailableException("merge down", null);
    var merge = queue(TYPE, ids(2));
    processor.processNext();
    assertThat(store.row(merge).errorCode).isEqualTo("RENDERER_UNAVAILABLE");
  }

  @Test
  void anUnexpectedCrashFailsTheJobWithoutKeepingTheMessage() {
    source.failure = new IllegalStateException("Nguyen Van A 012345678901");
    var job = queue(TYPE, ids(2));

    assertThat(processor.processNext()).isTrue();

    var row = store.row(job);
    assertThat(row.status).isEqualTo("FAILED");
    assertThat(row.errorCode).isEqualTo("INTERNAL_ERROR");
    assertThat(String.valueOf(row.errorCode)).doesNotContain("012345678901");
  }

  @Test
  void aRetryThrowsAwayWhatTheInterruptedAttemptStored() {
    var ids = ids(3);
    var job = queue(TYPE, ids);
    var first = store.claimNext(clock.instant(), clock.instant().plus(settings.lease()), 3).orElseThrow();
    String oldKey = "render-jobs/2026/10/" + job + "-1.pdf";
    storage.files.put("LOCAL:" + oldKey, new byte[] {1});
    store.addPart(
        first.id(),
        first.lockToken(),
        1,
        2,
        new NewFile(UUID.randomUUID(), "LOCAL", oldKey, "part-1.pdf", "application/pdf", 1, new byte[32]),
        clock.instant().plus(settings.lease()));
    clock.advance(Duration.ofMinutes(10));

    assertThat(processor.processNext()).isTrue();

    var row = store.row(job);
    assertThat(row.attempts).isEqualTo(2);
    assertThat(row.status).isEqualTo("SUCCEEDED");
    assertThat(storage.deleted).contains(oldKey);
    assertThat(row.parts).hasSize(2);
    assertThat(stored(row, 1)).isEqualTo(tag(ids.get(0)) + tag(ids.get(1)));
  }

  @Test
  void stopsWithoutWritingWhenAnotherWorkerOwnsTheJobNow() {
    var job = queue(TYPE, ids(6));
    store.afterProgress = row -> false;

    processor.processNext();

    var row = store.row(job);
    assertThat(row.status).isEqualTo("RUNNING");
    assertThat(row.finishedAt).isNull();
    assertThat(source.chunks).hasSize(1);
  }

  @Test
  void failsJobsThatRanOutOfAttemptsInsteadOfTakingThemAgain() {
    var job = queue(TYPE, ids(2));
    for (int i = 0; i < 3; i++) {
      assertThat(store.claimNext(clock.instant(), clock.instant().plus(settings.lease()), 3)).isPresent();
      clock.advance(Duration.ofMinutes(6));
    }

    assertThat(processor.processNext()).isFalse();

    assertThat(store.row(job).status).isEqualTo("FAILED");
    assertThat(store.row(job).errorCode).isEqualTo("ATTEMPTS_EXHAUSTED");
  }

  @Test
  void takesTheOldestWaitingJobFirst() {
    var older = queue(TYPE, ids(1));
    clock.advance(Duration.ofSeconds(1));
    var newer = queue(TYPE, ids(1));

    processor.processNext();

    assertThat(store.row(older).status).isEqualTo("SUCCEEDED");
    assertThat(store.row(newer).status).isEqualTo("QUEUED");
  }
}
