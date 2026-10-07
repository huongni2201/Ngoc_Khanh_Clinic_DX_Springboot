package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import java.util.List;
import java.util.UUID;

/**
 * Machine keys, order and layout of the examination detail Excel contract V1 shared by the reader
 * and the writer.
 *
 * <p>Row 1 holds Vietnamese labels, row 2 (hidden) the machine keys and the data starts at row 3.
 * Columns A and B are hidden and carry the Participant identifier and row version the file is
 * matched by; the service columns follow the fixed columns and are keyed {@code svc:<batch service
 * id>}.
 */
final class ExaminationDetailExcelColumns {
  static final String DETAIL_SHEET = "ChiTietKham";
  static final String GUIDE_SHEET = "HuongDan";

  static final String PARTICIPANT_ID = "participant_id";
  static final String ROW_VERSION = "row_version";
  static final String SEQ = "seq";
  static final String PARTICIPANT_CODE = "participant_code";
  static final String FULL_NAME = "full_name";
  static final String DATE_OF_BIRTH = "date_of_birth";
  static final String SEX = "sex";
  static final String IDENTIFICATION_NUMBER = "identification_number";
  static final String DEPARTMENT_NAME = "department_name";
  static final String POSITION_NAME = "position_name";
  static final String EXAMINATION_DATE = "examination_date";
  static final String ACTUAL_EXAMINATION_DATE = "actual_examination_date";

  /** Fixed columns in order; the reader checks the key row against them. */
  static final List<String> FIXED_KEYS =
      List.of(
          PARTICIPANT_ID,
          ROW_VERSION,
          SEQ,
          PARTICIPANT_CODE,
          FULL_NAME,
          DATE_OF_BIRTH,
          SEX,
          IDENTIFICATION_NUMBER,
          DEPARTMENT_NAME,
          POSITION_NAME,
          EXAMINATION_DATE,
          ACTUAL_EXAMINATION_DATE);

  /** Vietnamese labels of the fixed columns, in the same order as {@link #FIXED_KEYS}. */
  static final List<String> FIXED_LABELS =
      List.of(
          "ID người khám (ẩn)",
          "Phiên bản (ẩn)",
          "STT",
          "Mã người khám",
          "Họ tên",
          "Ngày sinh",
          "Giới tính",
          "CCCD",
          "Phòng ban",
          "Vị trí",
          "Ngày khám dự kiến",
          "Ngày khám thực tế");

  static final int ACTUAL_DATE_COLUMN = FIXED_KEYS.indexOf(ACTUAL_EXAMINATION_DATE);
  static final int FIRST_SERVICE_COLUMN = FIXED_KEYS.size();
  /** Zero-based worksheet rows. */
  static final int LABEL_ROW = 0;

  static final int KEY_ROW = 1;
  static final int FIRST_DATA_ROW = 2;

  /** Columns frozen at the left: up to and including the full name. */
  static final int FROZEN_COLUMNS = FIXED_KEYS.indexOf(FULL_NAME) + 1;

  static final String SERVICE_KEY_PREFIX = "svc:";
  static final String PERFORMED_MARK = "X";

  /** Metadata keys in the first column of the guide sheet, rows 1 to 3. */
  static final String META_TEMPLATE_VERSION = "templateVersion";

  static final String META_BATCH_ID = "batchId";
  static final String META_EXPORTED_AT = "exportedAt";

  static String serviceKey(UUID batchServiceId) {
    return SERVICE_KEY_PREFIX + batchServiceId;
  }

  private ExaminationDetailExcelColumns() {}
}
