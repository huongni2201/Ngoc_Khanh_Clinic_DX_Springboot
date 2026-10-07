package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ParticipantImportProperties;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Checks an uploaded XLSX package before any cell is read: size, the OOXML signature, the entry
 * count and extracted size budgets, and the absence of macros, embedded objects and external links.
 * It is shared by every Excel reader of the application so they all apply the same budgets, and it
 * never evaluates or logs file content.
 */
final class OoxmlPackageGuard {
  private static final byte[] ZIP_SIGNATURE = {'P', 'K', 3, 4};
  private static final String NOT_XLSX = "The file is not a valid XLSX workbook";

  private final ParticipantImportProperties limits;

  OoxmlPackageGuard(ParticipantImportProperties limits) {
    this.limits = limits;
  }

  /**
   * Rejects oversized, non-OOXML, macro-enabled, linked or zip-bomb-like packages.
   *
   * @throws MaxUploadSizeExceededException when the compressed file is larger than the budget
   * @throws ApplicationException of type {@code INVALID_INPUT} for every other rejection
   */
  void check(byte[] bytes) {
    if (bytes.length > limits.maxFileBytes())
      throw new MaxUploadSizeExceededException(limits.maxFileBytes());
    if (bytes.length < ZIP_SIGNATURE.length) throw invalid(NOT_XLSX);
    for (int i = 0; i < ZIP_SIGNATURE.length; i++)
      if (bytes[i] != ZIP_SIGNATURE[i]) throw invalid(NOT_XLSX);

    int entries = 0;
    long total = 0;
    boolean hasContentTypes = false;
    boolean hasWorkbook = false;
    byte[] buffer = new byte[8192];
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        if (++entries > limits.maxZipEntries()) throw invalid("The workbook contains too many parts");
        String name = entry.getName();
        if (name.startsWith("xl/vbaProject")
            || name.startsWith("xl/externalLinks/")
            || name.startsWith("xl/embeddings/"))
          throw invalid("Macros, embedded objects and external links are not supported");
        hasContentTypes |= "[Content_Types].xml".equals(name);
        hasWorkbook |= "xl/workbook.xml".equals(name);
        long entryBytes = 0;
        for (int read = zip.read(buffer); read != -1; read = zip.read(buffer)) {
          entryBytes += read;
          total += read;
          if (entryBytes > limits.maxEntryBytes() || total > limits.maxTotalBytes())
            throw invalid("The workbook is too large once extracted");
        }
      }
    } catch (IOException | RuntimeException unreadable) {
      if (unreadable instanceof ApplicationException rejected) throw rejected;
      throw invalid(NOT_XLSX);
    }
    if (!hasContentTypes || !hasWorkbook) throw invalid(NOT_XLSX);
  }

  private static ApplicationException invalid(String message) {
    return new ApplicationException(ApplicationException.Type.INVALID_INPUT, message);
  }
}
