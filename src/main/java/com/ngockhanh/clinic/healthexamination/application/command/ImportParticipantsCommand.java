package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.UUID;

/**
 * Input of a Participant Excel import. The workbook bytes exist only at this I/O boundary and are
 * copied on construction and on read.
 *
 * @param workbook raw XLSX bytes
 * @param rowVersion agreement configuration version the caller expects
 * @param idempotencyKey client-generated key that makes a retry safe
 * @param sessionId optional session the imported Participants are scheduled into in the same commit
 */
public record ImportParticipantsCommand(
    byte[] workbook, Long rowVersion, UUID idempotencyKey, UUID sessionId) {
  public ImportParticipantsCommand {
    workbook = workbook == null ? null : workbook.clone();
  }

  /** An import that schedules nobody. */
  public ImportParticipantsCommand(byte[] workbook, Long rowVersion, UUID idempotencyKey) {
    this(workbook, rowVersion, idempotencyKey, null);
  }

  @Override
  public byte[] workbook() {
    return workbook == null ? null : workbook.clone();
  }
}
