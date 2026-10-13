package com.ngockhanh.clinic.document.application.port;

import java.util.List;

/** Joins PDF files into one, keeping their order. */
public interface PdfMerger {
  /**
   * @param pdfs the files in the order they must appear; at least one
   * @return the merged PDF
   * @throws com.ngockhanh.clinic.shared.exception.DependencyUnavailableException when the merger
   *     cannot be reached, times out or rejects a file
   */
  byte[] merge(List<byte[]> pdfs);
}
