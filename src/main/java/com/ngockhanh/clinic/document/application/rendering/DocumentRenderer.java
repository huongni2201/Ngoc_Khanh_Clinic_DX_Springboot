package com.ngockhanh.clinic.document.application.rendering;

import java.util.Map;

/** Renders one document from the template version that is in effect now. */
public interface DocumentRenderer {
  /**
   * Fills the effective template of a document type with the values and converts it to PDF.
   *
   * @param documentType code of the document type
   * @param values placeholder key to text; a missing key is printed as an empty string
   * @return the PDF and the template version used
   * @throws com.ngockhanh.clinic.shared.exception.ResourceNotFoundException when no template
   *     version is in effect for the type
   * @throws com.ngockhanh.clinic.shared.exception.DependencyUnavailableException when the PDF
   *     converter cannot be reached or fails
   */
  RenderedDocument render(String documentType, Map<String, String> values);

  /**
   * Pins the template version in effect now so that many records can be rendered with the same one.
   *
   * @param documentType code of the document type
   * @return a session that renders with the pinned version
   * @throws com.ngockhanh.clinic.shared.exception.ResourceNotFoundException when no template
   *     version is in effect for the type
   */
  RenderSession open(String documentType);
}
