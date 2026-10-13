package com.ngockhanh.clinic.document.application.service;

import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.port.PdfMerger;
import com.ngockhanh.clinic.document.application.port.RenderJobStore;
import com.ngockhanh.clinic.document.application.rendering.DocumentRenderer;
import com.ngockhanh.clinic.document.application.rendering.RenderSession;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs one background print job at a time: loads the records chunk by chunk, renders each with the
 * template version pinned for the whole job, merges every {@code partSize} PDFs into a part, stores
 * it, and saves progress. Nothing here holds a database transaction while a PDF is converted. A
 * record that cannot be printed is skipped with a reason and the job goes on; only a converter that
 * keeps failing, or a storage fault, fails the job.
 */
@Service
public class RenderJobProcessor {
  private static final Logger log = LoggerFactory.getLogger(RenderJobProcessor.class);
  static final String PDF_CONTENT_TYPE = "application/pdf";
  /** Reasons a record is skipped by the processor itself. */
  public static final String TOO_LONG = "TOO_LONG";

  public static final String RENDER_FAILED = "RENDER_FAILED";
  /** Consecutive converter outages after which the job gives up. */
  private static final int MAX_CONSECUTIVE_OUTAGES = 3;

  private final RenderJobStore store;
  private final DocumentRenderer renderer;
  private final DocumentStorage storage;
  private final PdfMerger merger;
  private final List<DocumentValuesSource> sources;
  private final RenderJobSettings settings;
  private final Clock clock;

  public RenderJobProcessor(
      RenderJobStore store,
      DocumentRenderer renderer,
      DocumentStorage storage,
      PdfMerger merger,
      List<DocumentValuesSource> sources,
      RenderJobSettings settings,
      Clock clock) {
    this.store = store;
    this.renderer = renderer;
    this.storage = storage;
    this.merger = merger;
    this.sources = sources;
    this.settings = settings;
    this.clock = clock;
  }

  /**
   * Takes the next waiting job and runs it to its end.
   *
   * @return false when no job was waiting
   */
  public boolean processNext() {
    var now = clock.instant();
    int exhausted = store.failExhausted(now, settings.maxAttempts(), now.plus(settings.retention()));
    if (exhausted > 0) log.warn("Render jobs failed after their last attempt: count={}", exhausted);
    Optional<RenderJobStore.Claimed> claimed =
        store.claimNext(now, now.plus(settings.lease()), settings.maxAttempts());
    if (claimed.isEmpty()) return false;
    var job = claimed.get();
    try {
      run(job);
    } catch (RuntimeException e) {
      // The job is failed without its cause leaving the log: messages may hold personal data.
      log.error(
          "Render job crashed: jobId={}, type={}", job.id(), e.getClass().getSimpleName());
      fail(job, new Progress(), "INTERNAL_ERROR");
    }
    return true;
  }

  private void run(RenderJobStore.Claimed job) {
    if (job.attempt() > 1) discardParts(job.id());
    var source =
        sources.stream().filter(s -> s.documentType().equals(job.documentType())).findFirst();
    if (source.isEmpty()) {
      fail(job, new Progress(), "UNSUPPORTED_DOCUMENT_TYPE");
      return;
    }
    RenderSession session;
    try {
      session = renderer.open(job.documentType());
    } catch (ResourceNotFoundException e) {
      fail(job, new Progress(), "NO_TEMPLATE");
      return;
    } catch (RuntimeException e) {
      fail(job, new Progress(), "TEMPLATE_UNAVAILABLE");
      return;
    }
    if (!store.recordTemplateVersion(job.id(), job.lockToken(), session.templateVersionId())) {
      lostLease(job);
      return;
    }

    var progress = new Progress();
    List<byte[]> pending = new ArrayList<>();
    int partNo = 0;
    int consecutiveOutages = 0;
    List<UUID> ids = job.recordIds();
    for (int from = 0; from < ids.size(); from += settings.chunkSize()) {
      List<UUID> chunk = ids.subList(from, Math.min(ids.size(), from + settings.chunkSize()));
      DocumentValuesSource.Loaded loaded;
      try {
        loaded = source.get().load(job.context(), chunk);
      } catch (ResourceNotFoundException e) {
        fail(job, progress, "SOURCE_NOT_FOUND");
        return;
      } catch (ConflictException e) {
        fail(job, progress, "SOURCE_NOT_PRINTABLE");
        return;
      }
      progress.skipped.addAll(loaded.skipped());
      for (var item : loaded.items()) {
        try {
          pending.add(session.render(item.values()));
          consecutiveOutages = 0;
        } catch (DependencyUnavailableException e) {
          if (++consecutiveOutages >= MAX_CONSECUTIVE_OUTAGES) {
            fail(job, progress, "RENDERER_UNAVAILABLE");
            return;
          }
          progress.skipped.add(new DocumentValuesSource.Skipped(item.id(), RENDER_FAILED));
        } catch (ConflictException e) {
          progress.skipped.add(new DocumentValuesSource.Skipped(item.id(), TOO_LONG));
        } catch (RuntimeException e) {
          progress.skipped.add(new DocumentValuesSource.Skipped(item.id(), RENDER_FAILED));
        }
        if (pending.size() == settings.partSize()) {
          String failure = storePart(job, ++partNo, pending);
          if (failure != null) {
            fail(job, progress, failure);
            return;
          }
          pending = new ArrayList<>();
        }
      }
      progress.processed = Math.min(ids.size(), from + chunk.size());
      if (!store.progress(
          job.id(),
          job.lockToken(),
          progress.processed,
          progress.skipped,
          clock.instant().plus(settings.lease()))) {
        lostLease(job);
        return;
      }
    }
    if (!pending.isEmpty()) {
      String failure = storePart(job, ++partNo, pending);
      if (failure != null) {
        fail(job, progress, failure);
        return;
      }
    }
    if (partNo == 0) {
      fail(job, progress, "NOTHING_TO_PRINT");
      return;
    }
    var now = clock.instant();
    if (!store.finish(
        job.id(),
        job.lockToken(),
        true,
        null,
        ids.size(),
        progress.skipped,
        now,
        now.plus(settings.retention()))) lostLease(job);
    else
      log.info(
          "Render job done: jobId={}, parts={}, skipped={}", job.id(), partNo, progress.skipped.size());
  }

  /** Merges, stores and registers one part; returns an error code, or null on success. */
  private String storePart(RenderJobStore.Claimed job, int partNo, List<byte[]> pdfs) {
    byte[] content;
    try {
      content = pdfs.size() == 1 ? pdfs.get(0) : merger.merge(pdfs);
    } catch (DependencyUnavailableException e) {
      return "RENDERER_UNAVAILABLE";
    }
    ZonedDateTime at = clock.instant().atZone(ZoneOffset.UTC);
    String key =
        String.format("render-jobs/%04d/%02d/%s-%d.pdf", at.getYear(), at.getMonthValue(), job.id(), partNo);
    byte[] checksum;
    try {
      checksum = storage.write(RenderJobService.OUTPUT_PROVIDER, key, content);
    } catch (RuntimeException e) {
      return "STORAGE_FAILED";
    }
    var file =
        new NewFile(
            UuidV7Generator.generate(),
            RenderJobService.OUTPUT_PROVIDER,
            key,
            "part-" + partNo + ".pdf",
            PDF_CONTENT_TYPE,
            content.length,
            checksum);
    if (!store.addPart(
        job.id(), job.lockToken(), partNo, pdfs.size(), file, clock.instant().plus(settings.lease()))) {
      deleteQuietly(key);
      return "LEASE_LOST";
    }
    return null;
  }

  private void fail(RenderJobStore.Claimed job, Progress progress, String errorCode) {
    if ("LEASE_LOST".equals(errorCode)) {
      lostLease(job);
      return;
    }
    var now = clock.instant();
    boolean saved =
        store.finish(
            job.id(),
            job.lockToken(),
            false,
            errorCode,
            progress.processed,
            progress.skipped,
            now,
            now.plus(settings.retention()));
    if (!saved) lostLease(job);
    else log.warn("Render job failed: jobId={}, errorCode={}", job.id(), errorCode);
  }

  private void lostLease(RenderJobStore.Claimed job) {
    log.warn("Render job lease lost, another worker owns it now: jobId={}", job.id());
  }

  /** Removes what an earlier, interrupted attempt stored. */
  private void discardParts(UUID jobId) {
    for (var part : store.findParts(jobId)) deleteQuietly(part.storageKey());
    store.deleteParts(jobId);
  }

  private void deleteQuietly(String key) {
    try {
      storage.delete(RenderJobService.OUTPUT_PROVIDER, key);
    } catch (RuntimeException e) {
      log.warn("Stored print file could not be deleted: key={}", key);
    }
  }

  private static final class Progress {
    int processed;
    final List<DocumentValuesSource.Skipped> skipped = new ArrayList<>();
  }
}
