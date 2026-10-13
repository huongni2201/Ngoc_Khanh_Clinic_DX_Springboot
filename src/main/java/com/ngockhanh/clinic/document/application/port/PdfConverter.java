package com.ngockhanh.clinic.document.application.port;

/** Converts a Word document to PDF. */
public interface PdfConverter {
  /**
   * @param docx the Word document
   * @return the PDF bytes
   * @throws com.ngockhanh.clinic.shared.exception.DependencyUnavailableException when the converter
   *     cannot be reached, times out or rejects the document
   */
  byte[] toPdf(byte[] docx);
}
