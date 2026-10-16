package com.ngockhanh.clinic.document.application.contract;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supplies the placeholder values of many records of one document type to a background print job.
 * The business module that owns the records implements it as a Spring bean. A job holds only
 * identifiers; the personal data are read here, when the job runs.
 */
public interface DocumentValuesSource {
  /** Stable code of the document type served, as in {@link DocumentTypeContract#documentType()}. */
  String documentType();

  /**
   * Loads the values of the records a job asked for.
   *
   * @param context identifiers the job was created with (for example organization and agreement)
   * @param ids record identifiers, at most one chunk
   * @return the records that can be printed, in the order of {@code ids}, and the ones left out
   */
  Loaded load(Map<String, String> context, List<UUID> ids);

  /** One record ready to print. */
  record Item(UUID id, Map<String, String> values) {
    public Item {
      values = Map.copyOf(values);
    }
  }

  /** One record left out, with a short machine-readable reason such as {@code CANCELLED}. */
  record Skipped(UUID id, String reason) {}

  /** The outcome of one {@link #load} call. */
  record Loaded(List<Item> items, List<Skipped> skipped) {
    public Loaded {
      items = List.copyOf(items);
      skipped = List.copyOf(skipped);
    }
  }
}
