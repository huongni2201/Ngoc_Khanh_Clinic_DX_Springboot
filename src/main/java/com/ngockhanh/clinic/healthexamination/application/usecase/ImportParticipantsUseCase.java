package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.ImportParticipantsCommand;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantExcelReader;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantImportRow;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantWorkbook;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantImportCommitter;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantImportFingerprint;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Adds the Participants of an Excel workbook to an agreement roster; add-only and all-or-nothing.
 *
 * <p>The caller must hold the Participant import permission. The workbook is parsed and every row
 * is checked against the domain outside the write transaction; the first problem, in a fixed order,
 * rejects the whole file with the Excel row number and field but never the rejected value. The
 * commit then runs in {@link ParticipantImportCommitter}'s single transaction, which rechecks the
 * agreement, applies idempotency and the expected agreement version, rejects identification numbers
 * that already exist in the agreement (including cancelled Participants) and inserts every row
 * together with the import history and the audit event, or nothing at all. Patients and Encounters
 * are never created or linked. A retry with the same key and identical request returns the stored
 * result.
 *
 * <p>When the command names a session, the new Participants are also scheduled into it in the same
 * transaction. The caller then needs the assignment create permission too, and a session that is
 * missing, cancelled or serves another agreement rejects the whole import.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportParticipantsUseCase {
  private final ParticipantAccessPolicy access;
  private final SessionAccessPolicy sessionAccess;
  private final ParticipantExcelReader reader;
  private final ParticipantImportCommitter committer;

  /**
   * Imports the workbook.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param command workbook bytes, expected agreement version and idempotency key
   * @param principal authenticated staff account
   * @return the safe import result; a replay returns the originally stored result
   * @throws ApplicationException {@code ACCESS_DENIED} without the import permission (and, when a
   *     session is named, the assignment create permission), or {@code INVALID_INPUT} when the file
   *     or a row is not acceptable
   * @throws IllegalArgumentException when an argument is null or the version is negative
   * @throws ResourceNotFoundException when the organization or the agreement is not found
   * @throws ConflictException when the agreement does not accept imports, an identification number
   *     is duplicated in the file or already in the agreement, or the key was used for another
   *     request
   * @throws ConcurrentUpdateException when the expected agreement version is stale
   */
  public ParticipantImportResponse execute(
      UUID organizationId,
      UUID agreementId,
      ImportParticipantsCommand command,
      UserPrincipal principal) {
    access.requireImport(principal);
    if (command != null && command.sessionId() != null)
      sessionAccess.require(principal, SessionAccessPolicy.ASSIGNMENT_CREATE);
    if (organizationId == null || agreementId == null || command == null)
      throw new IllegalArgumentException("Organization ID, agreement ID and command are required");
    byte[] workbookBytes = command.workbook();
    Long rowVersion = command.rowVersion();
    if (workbookBytes == null
        || workbookBytes.length == 0
        || rowVersion == null
        || rowVersion < 0
        || command.idempotencyKey() == null)
      throw new IllegalArgumentException("Workbook, row version and idempotency key are required");

    ParticipantWorkbook workbook = reader.read(workbookBytes);
    if (workbook.templateVersion() != ParticipantWorkbook.TEMPLATE_VERSION
        || !agreementId.equals(workbook.templateAgreementId())
        || workbook.templateAgreementVersion() != rowVersion)
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT,
          "The workbook does not match this agreement version; download the template again");

    List<ParticipantImportCommitter.Row> rows = validate(workbook.rows());
    var outcome =
        committer.commit(
            new ParticipantImportCommitter.Request(
                organizationId,
                agreementId,
                rowVersion,
                command.idempotencyKey(),
                ParticipantImportFingerprint.of(
                    organizationId,
                    agreementId,
                    rowVersion,
                    ParticipantWorkbook.TEMPLATE_VERSION,
                    workbookBytes,
                    command.sessionId()),
                rows,
                principal.userId(),
                command.sessionId()));
    var receipt = outcome.receipt();
    if (outcome.replayed())
      log.info(
          "Participant import replayed: organizationId={}, agreementId={}, importJobId={}",
          organizationId,
          agreementId,
          receipt.importJobId());
    else
      log.info(
          "Participant import committed: organizationId={}, agreementId={}, importJobId={}, createdCount={}",
          organizationId,
          agreementId,
          receipt.importJobId(),
          receipt.createdCount());
    return ParticipantImportResponse.from(receipt, command.sessionId());
  }

  /** Applies the domain invariants row by row and rejects duplicates inside the file. */
  private static List<ParticipantImportCommitter.Row> validate(List<ParticipantImportRow> source) {
    List<ParticipantImportCommitter.Row> rows = new ArrayList<>(source.size());
    Map<String, Integer> firstRowOfIdentity = new HashMap<>();
    for (ParticipantImportRow row : source) {
      IdentificationNumber identity;
      try {
        identity = IdentificationNumber.of(row.identificationNumber());
      } catch (IllegalArgumentException invalid) {
        throw rejected(row.rowNumber(), "identification_number must be 1 to 20 digits");
      }
      Roster roster;
      try {
        roster =
            new Roster(
                null,
                row.fullName(),
                row.dateOfBirth(),
                row.sex(),
                identity,
                row.identificationIssueDate(),
                row.identificationIssuePlace(),
                row.ethnicity(),
                row.phone(),
                row.email(),
                row.address(),
                row.workplace(),
                row.departmentName(),
                row.positionName(),
                row.note());
      } catch (IllegalArgumentException invalid) {
        throw rejected(
            row.rowNumber(), "full_name, sex, department_name and position_name are not valid");
      }
      Integer first = firstRowOfIdentity.putIfAbsent(identity.value(), row.rowNumber());
      if (first != null)
        throw new ConflictException(
            "Duplicate participant identity at rows " + first + " and " + row.rowNumber());
      rows.add(new ParticipantImportCommitter.Row(row.rowNumber(), roster));
    }
    return rows;
  }

  private static ApplicationException rejected(int rowNumber, String problem) {
    return new ApplicationException(
        ApplicationException.Type.INVALID_INPUT, "Row " + rowNumber + ": " + problem);
  }
}
