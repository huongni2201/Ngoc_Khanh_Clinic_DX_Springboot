package com.ngockhanh.clinic.document.application.rendering;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Queues background print jobs. The job runs later; the caller only gets its identifier. */
public interface RenderJobs {
  /** Context key under which the creator may put a base download name ending in {@code .pdf}. */
  String FILE_NAME_KEY = "fileName";

  /**
   * Queues a job that renders one document per record and merges them into PDF parts. The same
   * requester and idempotency key with the same request returns the job already queued.
   *
   * @param request what to print and for whom
   * @return the queued job
   * @throws IllegalArgumentException when the request is malformed or has too many records
   * @throws com.ngockhanh.clinic.shared.exception.ConflictException when the key was used for a
   *     different request, or the requester already has too many jobs waiting or running
   * @throws com.ngockhanh.clinic.shared.exception.DependencyUnavailableException when document
   *     storage is not configured
   */
  Queued enqueue(NewRenderJob request);

  /**
   * A job to queue.
   *
   * @param documentType document type to print
   * @param requestedBy account that asked; only this account can read the job and its files
   * @param idempotencyKey client-chosen key that makes retries safe
   * @param context identifiers handed back to the document type's values source
   * @param recordIds the records to print, in print order, without duplicates
   */
  record NewRenderJob(
      String documentType,
      UUID requestedBy,
      UUID idempotencyKey,
      Map<String, String> context,
      List<UUID> recordIds) {
    public NewRenderJob {
      context = context == null ? Map.of() : Map.copyOf(context);
      // Not List.copyOf: a null element must reach validation and be refused as bad input.
      recordIds =
          recordIds == null
              ? List.of()
              : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(recordIds));
    }
  }

  /**
   * A queued job.
   *
   * @param jobId identifier to poll
   * @param replayed true when the key had already queued this same request
   */
  record Queued(UUID jobId, boolean replayed) {}
}
