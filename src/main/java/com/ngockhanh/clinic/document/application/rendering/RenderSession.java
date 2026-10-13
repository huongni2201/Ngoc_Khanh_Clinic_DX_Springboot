package com.ngockhanh.clinic.document.application.rendering;

import java.util.Map;
import java.util.UUID;

/**
 * A template version pinned for several renders: a long job prints every record with the same
 * version even if a newer one is published meanwhile.
 */
public interface RenderSession {
  /** Version of the template used by every {@link #render} call of this session. */
  UUID templateVersionId();

  /** Human-readable number of that version. */
  int templateVersionNo();

  /**
   * Fills the pinned template and converts it to PDF.
   *
   * @param values placeholder key to text; a missing key is printed as an empty string
   * @return the PDF bytes
   * @throws com.ngockhanh.clinic.shared.exception.ConflictException when the form does not fit its
   *     pages
   * @throws com.ngockhanh.clinic.shared.exception.DependencyUnavailableException when the PDF
   *     converter cannot be reached or fails
   */
  byte[] render(Map<String, String> values);
}
