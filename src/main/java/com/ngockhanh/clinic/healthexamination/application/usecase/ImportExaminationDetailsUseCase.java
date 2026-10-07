package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.ImportExaminationDetailsCommand;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailExcelReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailWorkbook;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailImportResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailImportCommitter;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailImportFingerprint;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Records the services performed for the Participants of an Excel file, overwriting their service
 * reconciliation by file; all-or-nothing.
 *
 * <p>The caller must hold the service reconcile permission. The workbook is parsed outside the
 * write transaction; the first problem, in a fixed order, rejects the whole file with the Excel row
 * number and field but never the rejected value. The commit then runs in {@link
 * ExaminationDetailImportCommitter}'s single transaction, which rechecks the batch, applies
 * idempotency, matches every row to a Participant by identifier and row version, applies the file
 * as the new reconciliation state of each Participant in it, and writes the import history and the
 * audit event, or nothing at all. Participants that are not in the file are never touched. Only
 * attendance and service reconciliation are written; clinical results, conclusions and record
 * versions are out of scope. A retry with the same key and identical request returns the stored
 * result.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportExaminationDetailsUseCase {
  private final ExaminationDetailAccessPolicy access;
  private final ExaminationDetailExcelReader reader;
  private final ExaminationDetailImportCommitter committer;

  /**
   * Imports the workbook.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param command workbook bytes and idempotency key
   * @param principal authenticated staff account
   * @return the safe import result; a replay returns the originally stored result
   * @throws ApplicationException {@code ACCESS_DENIED} without the reconcile permission, or {@code
   *     INVALID_INPUT} when the file, a row or an actual date is not acceptable
   * @throws IllegalArgumentException when an argument is null or empty
   * @throws ResourceNotFoundException when the organization or the batch is not found
   * @throws ConflictException when the batch does not accept changes, a Participant is not in the
   *     batch or is cancelled, a newly marked service is no longer offered, or the key was used for
   *     another request
   * @throws ConcurrentUpdateException when a Participant changed after the file was exported
   */
  public ExaminationDetailImportResponse execute(
      UUID organizationId,
      UUID batchId,
      ImportExaminationDetailsCommand command,
      UserPrincipal principal) {
    access.requireServiceReconcile(principal);
    if (organizationId == null || batchId == null || command == null)
      throw new IllegalArgumentException("Organization ID, batch ID and command are required");
    byte[] workbookBytes = command.workbook();
    if (workbookBytes == null || workbookBytes.length == 0 || command.idempotencyKey() == null)
      throw new IllegalArgumentException("Workbook and idempotency key are required");

    ExaminationDetailWorkbook workbook = reader.read(workbookBytes);
    var outcome =
        committer.commit(
            new ExaminationDetailImportCommitter.Request(
                organizationId,
                batchId,
                command.idempotencyKey(),
                ExaminationDetailImportFingerprint.of(
                    organizationId,
                    batchId,
                    ExaminationDetailWorkbook.TEMPLATE_VERSION,
                    workbookBytes),
                workbook.templateVersion(),
                workbook.declaredServiceIds(),
                workbook.rows(),
                principal.userId()));
    var receipt = outcome.receipt();
    if (outcome.replayed())
      log.debug(
          "Examination detail import replayed: organizationId={}, batchId={}, importJobId={}",
          organizationId,
          batchId,
          receipt.importJobId());
    else
      log.info(
          "Examination detail import committed: organizationId={}, batchId={}, importJobId={}, rows={}, updated={}",
          organizationId,
          batchId,
          receipt.importJobId(),
          receipt.totalRows(),
          receipt.updatedParticipants());
    return ExaminationDetailImportResponse.from(receipt);
  }
}
