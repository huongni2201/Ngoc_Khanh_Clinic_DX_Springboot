package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import java.util.List;

/** Machine keys, order and layout of the Excel contract V1 shared by the reader and the writer. */
final class ParticipantExcelColumns {
  static final String PARTICIPANTS_SHEET = "Participants";
  static final String INSTRUCTIONS_SHEET = "Instructions";
  static final String EXAMINATION_DATES_NAME = "ExaminationDates";

  static final String PARTICIPANT_CODE = "participant_code";
  static final String FULL_NAME = "full_name";
  static final String DATE_OF_BIRTH = "date_of_birth";
  static final String SEX = "sex";
  static final String IDENTIFICATION_NUMBER = "identification_number";
  static final String PHONE = "phone";
  static final String EMAIL = "email";
  static final String DEPARTMENT_NAME = "department_name";
  static final String POSITION_NAME = "position_name";
  static final String EXAMINATION_DATE = "examination_date";

  /** Canonical order: the template writes columns in it and the reader reports errors in it. */
  static final List<String> ORDERED =
      List.of(
          PARTICIPANT_CODE,
          FULL_NAME,
          DATE_OF_BIRTH,
          SEX,
          IDENTIFICATION_NUMBER,
          PHONE,
          EMAIL,
          DEPARTMENT_NAME,
          POSITION_NAME,
          EXAMINATION_DATE);

  /** Metadata keys in the first column of the Instructions sheet, rows 1 to 3. */
  static final String META_TEMPLATE_VERSION = "templateVersion";
  static final String META_BATCH_ID = "batchId";
  static final String META_BATCH_ROW_VERSION = "batchRowVersion";

  /** Zero-based worksheet row of the first line of the examination date list. */
  static final int DATE_LIST_FIRST_ROW = 14;

  private ParticipantExcelColumns() {}
}
