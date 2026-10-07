package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.UUID;

/**
 * Input of an examination detail Excel import. The workbook bytes exist only at this I/O boundary
 * and are copied on construction and on read.
 *
 * @param workbook raw XLSX bytes
 * @param idempotencyKey client-generated key that makes a retry safe
 */
public record ImportExaminationDetailsCommand(byte[] workbook, UUID idempotencyKey) {
  public ImportExaminationDetailsCommand {
    workbook = workbook == null ? null : workbook.clone();
  }

  @Override
  public byte[] workbook() {
    return workbook == null ? null : workbook.clone();
  }
}
