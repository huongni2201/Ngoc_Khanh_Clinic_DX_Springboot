package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportFileStorage;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader.SpreadsheetFormat;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantRosterRowValidator;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportRowAction;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository.BatchParticipantRosterSnapshot;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateParticipantImportUseCase {
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationImportJobRepository jobs;
  private final ImportAttachmentMetadataRepository attachments;
  private final ImportFileStorage storage;
  private final ParticipantSpreadsheetReader spreadsheets;
  private final ParticipantRosterRowValidator rowValidator;
  private final HealthExaminationParticipantRepository participants;
  private final HealthExaminationBatchParticipantRepository batchParticipants;
  private final StoreValidatedParticipantImportUseCase storeValidatedImport;

  @Value("${clinic.health-examination.employee-import.max-rows:10000}")
  private int maxRows = 10_000;

  public ParticipantImportSummaryResponse execute(
      UUID organizationId,
      UUID batchId,
      UUID importId,
      UUID actorUserId,
      Map<String, Integer> requestedMapping) {
    if (organizationId == null
        || batchId == null
        || importId == null
        || actorUserId == null
        || requestedMapping == null) {
      throw new IllegalArgumentException("Participant import mapping details are required");
    }
    AggregateId organization = AggregateId.of(organizationId);
    AggregateId batchIdValue = AggregateId.of(batchId);
    AggregateId importJobId = AggregateId.of(importId);
    EnumMap<ParticipantImportField, Integer> columns = new EnumMap<>(ParticipantImportField.class);
    requestedMapping.forEach(
        (name, index) -> {
          try {
            columns.put(ParticipantImportField.valueOf(name), index);
          } catch (RuntimeException invalidField) {
            throw new IllegalArgumentException("Import column field is invalid", invalidField);
          }
        });
    ParticipantImportColumnMapping mapping = ParticipantImportColumnMapping.of(columns);

    var batch =
        batches
            .findByIdAndOrganizationId(batchIdValue, organization)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    if (!batch.status().allowsRosterImport()) {
      throw new BusinessRuleException("Roster import is not allowed for this batch state") {};
    }

    var job =
        jobs.findSummaryByIdAndBatchId(importJobId, batchIdValue)
            .orElseThrow(() -> new ResourceNotFoundException("Participant import"));

    if (job.type() != ImportType.PARTICIPANT_LIST
        || (job.status() != ImportStatus.UPLOADED && job.status() != ImportStatus.VALIDATED)
        || job.sourceFileAttachmentId() == null) {
      throw new BusinessRuleException("Participant import job is not ready for validation") {};
    }
    ImportAttachmentMetadata attachment =
        attachments
            .findByIdAndImportJobId(job.sourceFileAttachmentId().value(), importId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant import source file"));
    SpreadsheetFormat format = spreadsheetFormat(attachment.fileName());

    ParticipantSpreadsheetReader.SpreadsheetHeader header = readHeader(attachment, format);
    List<ParticipantSpreadsheetReader.SpreadsheetRow> sourceRows = readRows(attachment, format);
    List<HealthExaminationImportRow> rows = new ArrayList<>(sourceRows.size());
    for (ParticipantSpreadsheetReader.SpreadsheetRow sourceRow : sourceRows) {
      if (!hasContent(sourceRow.cells())) continue;
      if (rows.size() >= maxRows)
        throw new IllegalArgumentException("Spreadsheet exceeds configured row limit");
      rows.add(
          rowValidator.validate(
              new AggregateId(UuidV7Generator.generate()),
              sourceRow.rowNumber(),
              sourceRow.cells(),
              mapping));
    }
    if (rows.isEmpty()) throw new IllegalArgumentException("Spreadsheet contains no data rows");

    markDuplicateIdentificationNumbers(rows);
    resolveActions(rows, organization, batchIdValue);
    return storeValidatedImport.execute(
        organizationId, batchId, importId, actorUserId, mapping.columns(), rows, header.values());
  }

  private void resolveActions(
      List<HealthExaminationImportRow> rows, AggregateId organizationId, AggregateId batchId) {
    List<IdentificationNumber> identificationNumbers =
        rows.stream()
            .filter(HealthExaminationImportRow::isValid)
            .map(HealthExaminationImportRow::getIdentificationNumber)
            .distinct()
            .toList();
    Map<String, HealthExaminationParticipant> byIdentificationNumber = new HashMap<>();
    forEachChunk(
        identificationNumbers,
        chunk ->
            participants
                .findByOrganizationAndIdentificationNumbers(organizationId, chunk)
                .forEach(
                    person ->
                        byIdentificationNumber.put(person.identificationNumber().value(), person)));

    List<AggregateId> existingParticipantIds =
        byIdentificationNumber.values().stream()
            .map(HealthExaminationParticipant::id)
            .distinct()
            .toList();
    Map<AggregateId, BatchParticipantRosterSnapshot> snapshots = new HashMap<>();
    forEachChunk(
        existingParticipantIds,
        chunk ->
            batchParticipants
                .findRosterSnapshots(batchId, chunk)
                .forEach(snapshot -> snapshots.put(snapshot.participantId(), snapshot)));

    List<AggregateId> batchParticipantIds =
        snapshots.values().stream()
            .map(BatchParticipantRosterSnapshot::batchParticipantId)
            .distinct()
            .toList();
    Set<AggregateId> participantsWithRecords = new HashSet<>();
    forEachChunk(
        batchParticipantIds,
        chunk ->
            participantsWithRecords.addAll(
                batchParticipants.findBatchParticipantIdsWithHealthRecords(chunk)));

    for (HealthExaminationImportRow row : rows) {
      if (!row.isValid()) continue;
      HealthExaminationParticipant existing =
          byIdentificationNumber.get(row.getIdentificationNumber().value());
      BatchParticipantRosterSnapshot snapshot =
          existing == null ? null : snapshots.get(existing.id());
      row.setPreviewFingerprint(
          previewFingerprint(
              existing,
              snapshot,
              snapshot != null && participantsWithRecords.contains(snapshot.batchParticipantId())));
      if (existing == null) {
        row.setAppliedAction(ImportRowAction.CREATE);
        continue;
      }
      if (snapshot == null) {
        row.setAppliedAction(ImportRowAction.CREATE);
        continue;
      }
      if (!snapshot.identificationNumber().equals(row.getIdentificationNumber())) {
        row.reject("IDENTITY_CONFLICT");
        continue;
      }
      ImportRowAction action =
          sameRoster(row, existing, snapshot) ? ImportRowAction.UNCHANGED : ImportRowAction.UPDATE;
      row.setAppliedAction(action);
      if (action == ImportRowAction.UPDATE
          && participantsWithRecords.contains(snapshot.batchParticipantId())) {
        row.addWarning("HEALTH_RECORD_SNAPSHOT_UNCHANGED");
      }
    }
  }

  static boolean sameRoster(
      HealthExaminationImportRow row,
      HealthExaminationParticipant person,
      BatchParticipantRosterSnapshot snapshot) {
    if (!person.fullName().equals(row.getFullName())
        || !person.dateOfBirth().equals(row.getDateOfBirth())
        || !person.sex().equals(row.getSex())) return false;
    String occupation = row.getOccupation() == null ? person.occupation() : row.getOccupation();
    return java.util.Objects.equals(person.occupation(), occupation)
        && sameOptional(row.getOccupation(), snapshot.occupation())
        && java.util.Objects.equals(snapshot.fullName(), row.getFullName())
        && java.util.Objects.equals(snapshot.dateOfBirth(), row.getDateOfBirth())
        && java.util.Objects.equals(snapshot.sex(), row.getSex())
        && java.util.Objects.equals(snapshot.identificationNumber(), row.getIdentificationNumber())
        && sameOptional(
            row.getIdentificationNumberIssueDate(), snapshot.identificationNumberIssueDate())
        && sameOptional(
            row.getIdentificationNumberIssuePlace(), snapshot.identificationNumberIssuePlace())
        && sameOptional(row.getEthnicity(), snapshot.ethnicity())
        && sameOptional(row.getSubjectType(), snapshot.subjectType())
        && sameOptional(row.getPayerSource(), snapshot.payerSource())
        && sameOptional(row.getBloodGroup(), snapshot.bloodGroup())
        && sameOptional(row.getPhone(), snapshot.phone())
        && sameOptional(row.getAddressDetail(), snapshot.addressDetail())
        && sameOptional(row.getWorkplaceOrSchool(), snapshot.workplaceOrSchool())
        && sameOptional(row.getRosterNote(), snapshot.rosterNote());
  }

  static String previewFingerprint(
      HealthExaminationParticipant person,
      BatchParticipantRosterSnapshot snapshot,
      boolean hasHealthRecord) {
    List<Object> state = new ArrayList<>();
    Collections.addAll(state, person != null, snapshot != null, hasHealthRecord);
    if (person != null) {
      Collections.addAll(
          state,
          person.id().value(),
          person.organizationId().value(),
          person.participantCode(),
          person.identificationNumber().value(),
          person.fullName(),
          person.dateOfBirth(),
          person.sex(),
          person.departmentName(),
          person.jobTitle(),
          person.occupation(),
          person.status(),
          person.patientId() == null ? null : person.patientId().value());
    }
    if (snapshot != null) {
      Collections.addAll(
          state,
          snapshot.batchParticipantId().value(),
          snapshot.participantId().value(),
          snapshot.participantCode(),
          snapshot.departmentName(),
          snapshot.jobTitle(),
          snapshot.occupation(),
          snapshot.fullName(),
          snapshot.dateOfBirth(),
          snapshot.sex(),
          snapshot.identificationNumber().value(),
          snapshot.identificationNumberIssueDate(),
          snapshot.identificationNumberIssuePlace(),
          snapshot.ethnicity(),
          snapshot.subjectType(),
          snapshot.payerSource(),
          snapshot.bloodGroup(),
          snapshot.phone(),
          snapshot.province(),
          snapshot.ward(),
          snapshot.addressDetail(),
          snapshot.administrativeOccupation(),
          snapshot.workplaceOrSchool(),
          snapshot.healthExaminationReason(),
          snapshot.rosterNote());
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (Object value : state) {
        byte[] encoded = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(
            ByteBuffer.allocate(Integer.BYTES)
                .putInt(encoded == null ? -1 : encoded.length)
                .array());
        if (encoded != null) digest.update(encoded);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is unavailable", failure);
    }
  }

  private static boolean sameOptional(Object supplied, Object existing) {
    return supplied == null || java.util.Objects.equals(supplied, existing);
  }

  private static void markDuplicateIdentificationNumbers(List<HealthExaminationImportRow> rows) {
    Map<String, List<HealthExaminationImportRow>> grouped = new HashMap<>();
    for (HealthExaminationImportRow row : rows) {
      if (row.getIdentificationNumber() != null) {
        grouped
            .computeIfAbsent(row.getIdentificationNumber().value(), ignored -> new ArrayList<>())
            .add(row);
      }
    }
    grouped.values().stream()
        .filter(group -> group.size() > 1)
        .forEach(group -> group.forEach(row -> row.reject("DUPLICATE_IN_FILE")));
  }

  private ParticipantSpreadsheetReader.SpreadsheetHeader readHeader(
      ImportAttachmentMetadata attachment, SpreadsheetFormat format) {
    try (InputStream input = storage.open(attachment.storageKey())) {
      return spreadsheets.readHeader(input, format);
    } catch (IOException failure) {
      throw new IllegalStateException("Unable to read participant import source", failure);
    }
  }

  private List<ParticipantSpreadsheetReader.SpreadsheetRow> readRows(
      ImportAttachmentMetadata attachment, SpreadsheetFormat format) {
    List<ParticipantSpreadsheetReader.SpreadsheetRow> rows = new ArrayList<>();
    try (InputStream input = storage.open(attachment.storageKey())) {
      spreadsheets.readRows(input, format, rows::add);
      return rows;
    } catch (IOException failure) {
      throw new IllegalStateException("Unable to read participant import source", failure);
    } catch (RuntimeException failure) {
      throw new IllegalArgumentException("Spreadsheet content cannot be parsed", failure);
    }
  }

  private static boolean hasContent(Map<Integer, String> cells) {
    return cells.values().stream().anyMatch(value -> value != null && !value.isBlank());
  }

  private static SpreadsheetFormat spreadsheetFormat(String fileName) {
    String lowerName = fileName.toLowerCase(java.util.Locale.ROOT);
    if (lowerName.endsWith(".xls") && !lowerName.endsWith(".xlsx")) return SpreadsheetFormat.XLS;
    if (lowerName.endsWith(".xlsx")) return SpreadsheetFormat.XLSX;
    throw new IllegalArgumentException("Unsupported stored spreadsheet format");
  }

  private static <T> void forEachChunk(
      List<T> values, java.util.function.Consumer<List<T>> consumer) {
    for (int start = 0; start < values.size(); start += 400) {
      consumer.accept(values.subList(start, Math.min(start + 400, values.size())));
    }
  }
}
