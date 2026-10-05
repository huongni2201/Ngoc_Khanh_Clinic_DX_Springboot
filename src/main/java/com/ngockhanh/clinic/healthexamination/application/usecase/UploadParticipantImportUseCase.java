package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.command.UploadParticipantImportCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportUploadResponse;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantRosterHeaderMapper;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantRosterRowValidator;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadParticipantImportUseCase {
  private static final byte[] XLS_MAGIC = {
    (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
  };
  private static final byte[] XLSX_MAGIC = {'P', 'K', 3, 4};
  private static final long DEFAULT_MAX_FILE_BYTES = 10L * 1024 * 1024;

  private final HealthExaminationBatchRepository batches;
  private final ImportFileStorage storage;
  private final ParticipantSpreadsheetReader spreadsheets;
  private final ParticipantRosterHeaderMapper headerMapper;
  private final StoreValidatedParticipantImportUseCase storeValidated;
  private final ParticipantRosterRowValidator rowValidator;

  @Value("${clinic.health-examination.employee-import.max-file-size-bytes:10485760}")
  private long maxFileSizeBytes = DEFAULT_MAX_FILE_BYTES;

  public ParticipantImportUploadResponse execute(UploadParticipantImportCommand command) {
    if (command == null
        || command.organizationId() == null
        || command.batchId() == null
        || command.actorUserId() == null
        || command.content() == null) {
      throw new IllegalArgumentException("Import upload details are required");
    }
    if (command.sizeBytes() < 1 || command.sizeBytes() > maxFileSizeBytes) {
      throw new IllegalArgumentException("Import file size is outside the allowed limit");
    }

    AggregateId organizationId = AggregateId.of(command.organizationId());
    AggregateId batchId = AggregateId.of(command.batchId());
    HealthExaminationBatchReference batch =
        batches
            .findByIdAndOrganizationId(batchId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    requireRosterEditable(batch.status());

    UUID jobId = UuidV7Generator.generate();
    String fileName = safeFileName(command.fileName());
    SpreadsheetFormat format;
    PushbackInputStream input = new PushbackInputStream(command.content(), 8);
    try {
      format = detectFormat(fileName, input);
    } catch (IOException failure) {
      throw new IllegalArgumentException("Invalid spreadsheet file", failure);
    }

    ImportFileStorage.StoredFile stored;
    try {
      stored = storage.store(jobId, input, maxFileSizeBytes);
    } catch (IOException failure) {
      throw new IllegalStateException("Unable to store encrypted import file", failure);
    }

    try {
      ParticipantSpreadsheetReader.SpreadsheetHeader header;
      try (InputStream source = storage.open(stored.storageKey())) {
        header = spreadsheets.readHeader(source, format);
      }
      var mapping = ParticipantImportColumnMapping.of(headerMapper.suggest(header.values()));
      var rows = new ArrayList<HealthExaminationImportRow>();
      try (InputStream source = storage.open(stored.storageKey())) {
        spreadsheets.readRows(
            source,
            format,
            row -> {
              if (row.cells().values().stream().anyMatch(v -> v != null && !v.isBlank()))
                rows.add(
                    rowValidator.validate(
                        AggregateId.of(UuidV7Generator.generate()),
                        row.rowNumber(),
                        row.cells(),
                        mapping));
            });
      }
      if (rows.isEmpty()) throw new IllegalArgumentException("Spreadsheet contains no data rows");
      return storeValidated.execute(
          command.organizationId(),
          command.batchId(),
          command.actorUserId(),
          command.selectedBatchDayIds(),
          rows);
    } catch (RuntimeException failure) {
      throw failure;
    } catch (IOException failure) {
      throw new IllegalStateException("Unable to read encrypted import file", failure);
    } finally {
      deleteAfterFailure(stored.storageKey(), jobId);
    }
  }

  private static void requireRosterEditable(BatchStatus status) {
    if (!status.allowsRosterImport()) {
      throw new BusinessRuleException("Roster import is not allowed for this batch state") {};
    }
  }

  private static SpreadsheetFormat detectFormat(String fileName, PushbackInputStream input)
      throws IOException {
    String lowerName = fileName.toLowerCase(Locale.ROOT);
    byte[] prefix = input.readNBytes(8);
    input.unread(prefix);
    boolean xls = startsWith(prefix, XLS_MAGIC);
    boolean xlsx = startsWith(prefix, XLSX_MAGIC);
    if (lowerName.endsWith(".xls") && xls) return SpreadsheetFormat.XLS;
    if (lowerName.endsWith(".xlsx") && xlsx) return SpreadsheetFormat.XLSX;
    throw new IllegalArgumentException("File content does not match a supported Excel format");
  }

  private static boolean startsWith(byte[] value, byte[] prefix) {
    if (value.length < prefix.length) return false;
    for (int index = 0; index < prefix.length; index++) {
      if (value[index] != prefix[index]) return false;
    }
    return true;
  }

  private static String safeFileName(String fileName) {
    if (fileName == null || fileName.isBlank())
      throw new IllegalArgumentException("Import file name is required");
    String normalized = fileName.replace('\\', '/');
    String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).strip();
    if (baseName.length() > 255) baseName = baseName.substring(baseName.length() - 255);
    return baseName;
  }

  private static String mimeType(SpreadsheetFormat format) {
    return format == SpreadsheetFormat.XLS
        ? "application/vnd.ms-excel"
        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  }

  private void deleteAfterFailure(String storageKey, UUID jobId) {
    try {
      storage.delete(storageKey);
    } catch (IOException cleanupFailure) {
      log.warn("Encrypted import source cleanup failed: importId={}", jobId);
    }
  }
}
