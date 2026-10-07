package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.ImportExaminationDetailsCommand;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.UnsupportedFileTypeException;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart input of an examination detail import: one {@code .xlsx} file and the client's
 * idempotency key. It checks only the structure of the upload; the content is validated by the use
 * case.
 */
public record ImportExaminationDetailsRequest(MultipartFile file, UUID idempotencyKey) {
  private static final Set<String> ACCEPTED_CONTENT_TYPES =
      Set.of(ImportParticipantsRequest.XLSX_CONTENT_TYPE, "application/octet-stream");

  /**
   * Maps the upload to the application command and reads the workbook bytes.
   *
   * @throws ApplicationException {@code INVALID_INPUT} when the file is missing, empty or unreadable
   * @throws UnsupportedFileTypeException when the file is not an {@code .xlsx} workbook
   */
  public ImportExaminationDetailsCommand toCommand() {
    if (file == null || file.isEmpty())
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT, "A non-empty .xlsx file is required");
    String name = file.getOriginalFilename();
    if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      throw new UnsupportedFileTypeException("Only XLSX workbooks are supported");
    String type = file.getContentType();
    if (type != null && !type.isBlank()) {
      String mediaType = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
      if (!ACCEPTED_CONTENT_TYPES.contains(mediaType))
        throw new UnsupportedFileTypeException("Only XLSX workbooks are supported");
    }
    try {
      return new ImportExaminationDetailsCommand(file.getBytes(), idempotencyKey);
    } catch (IOException unreadable) {
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT, "The upload could not be read");
    }
  }
}
