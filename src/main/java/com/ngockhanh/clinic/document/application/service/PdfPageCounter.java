package com.ngockhanh.clinic.document.application.service;

import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts the pages of a PDF without a PDF library, by counting page objects. It is meant for the
 * plain, uncompressed object layout LibreOffice writes; a PDF that keeps its objects in compressed
 * object streams, or that is not a PDF, gives no answer instead of a wrong one.
 */
final class PdfPageCounter {
  private static final Pattern PAGE = Pattern.compile("/Type\\s*/Page(?![A-Za-z])");

  private PdfPageCounter() {}

  static OptionalInt count(byte[] pdf) {
    if (pdf == null || pdf.length < 8) return OptionalInt.empty();
    String text = new String(pdf, StandardCharsets.ISO_8859_1);
    if (!text.startsWith("%PDF-") || text.contains("/ObjStm")) return OptionalInt.empty();
    Matcher matcher = PAGE.matcher(text);
    int pages = 0;
    while (matcher.find()) pages++;
    return pages == 0 ? OptionalInt.empty() : OptionalInt.of(pages);
  }
}
