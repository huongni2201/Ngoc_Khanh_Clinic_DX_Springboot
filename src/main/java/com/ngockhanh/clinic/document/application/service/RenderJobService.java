package com.ngockhanh.clinic.document.application.service;

import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.RenderJobStore;
import com.ngockhanh.clinic.document.application.rendering.RenderJobs;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Queues background print jobs; the processor runs them later. */
@Service
public class RenderJobService implements RenderJobs {
  private static final Logger log = LoggerFactory.getLogger(RenderJobService.class);
  /** Where job output is kept; the only provider that can be written. */
  public static final String OUTPUT_PROVIDER = "LOCAL";

  private final RenderJobStore store;
  private final DocumentStorage storage;
  private final List<DocumentValuesSource> sources;
  private final RenderJobSettings settings;
  private final Clock clock;

  public RenderJobService(
      RenderJobStore store,
      DocumentStorage storage,
      List<DocumentValuesSource> sources,
      RenderJobSettings settings,
      Clock clock) {
    this.store = store;
    this.storage = storage;
    this.sources = sources;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Queued enqueue(NewRenderJob request) {
    validate(request);
    var existing = store.findByIdempotencyKey(request.requestedBy(), request.idempotencyKey());
    if (existing.isPresent()) return replay(existing.get(), request);
    if (!storage.writable(OUTPUT_PROVIDER))
      throw new DependencyUnavailableException("Document storage is not configured", null);
    if (store.countActive(request.requestedBy()) >= settings.maxActivePerRequester())
      throw new ConflictException("Too many print jobs are waiting or running; wait for one to finish");

    UUID id = UuidV7Generator.generate();
    boolean inserted =
        store.insert(
            id,
            request.documentType(),
            request.requestedBy(),
            request.idempotencyKey(),
            request.context(),
            request.recordIds(),
            clock.instant());
    if (!inserted) {
      // A concurrent retry with the same key won the insert.
      var winner =
          store
              .findByIdempotencyKey(request.requestedBy(), request.idempotencyKey())
              .orElseThrow(() -> new IllegalStateException("The print job was not saved"));
      return replay(winner, request);
    }
    log.info(
        "Render job queued: jobId={}, documentType={}, records={}",
        id,
        request.documentType(),
        request.recordIds().size());
    return new Queued(id, false);
  }

  private Queued replay(RenderJobStore.Job job, NewRenderJob request) {
    boolean same =
        job.documentType().equals(request.documentType())
            && job.context().equals(request.context())
            && job.recordIds().equals(request.recordIds());
    if (!same) throw new ConflictException("The Idempotency-Key was already used for a different request");
    return new Queued(job.id(), true);
  }

  private void validate(NewRenderJob request) {
    if (request.documentType() == null
        || request.documentType().isBlank()
        || request.requestedBy() == null
        || request.idempotencyKey() == null)
      throw new IllegalArgumentException("Document type, requester and idempotency key are required");
    if (sources.stream().noneMatch(source -> source.documentType().equals(request.documentType())))
      throw new IllegalArgumentException("Unsupported document type");
    var ids = request.recordIds();
    if (ids.isEmpty() || ids.size() > settings.maxRecords())
      throw new IllegalArgumentException("Between 1 and " + settings.maxRecords() + " records are required");
    if (ids.stream().anyMatch(java.util.Objects::isNull) || new HashSet<>(ids).size() != ids.size())
      throw new IllegalArgumentException("Record identifiers must be present and unique");
  }
}
