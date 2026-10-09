package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Machine keys, Vietnamese header labels, order and layout of the Excel contract V1 shared by the
 * reader and the writer. Machine keys stay in code and in error messages; the worksheet shows the
 * Vietnamese labels.
 */
final class ParticipantExcelColumns {
  static final String PARTICIPANTS_SHEET = "Participants";
  static final String INSTRUCTIONS_SHEET = "Instructions";
  static final String EXAMINATION_DATES_NAME = "ExaminationDates";

  /**
   * Row number column written first in the template. It only numbers the rows for the person
   * filling the file: the reader accepts it, never reads its cells and never requires it.
   */
  static final String ORDINAL = "ordinal";

  static final String FULL_NAME = "full_name";
  static final String DATE_OF_BIRTH = "date_of_birth";
  static final String SEX = "sex";
  static final String IDENTIFICATION_NUMBER = "identification_number";
  static final String IDENTIFICATION_ISSUE_DATE = "identification_issue_date";
  static final String IDENTIFICATION_ISSUE_PLACE = "identification_issue_place";
  static final String ETHNICITY = "ethnicity";
  static final String PHONE = "phone";
  static final String EMAIL = "email";
  static final String ADDRESS = "address";
  static final String WORKPLACE = "workplace";
  static final String DEPARTMENT_NAME = "department_name";
  static final String POSITION_NAME = "position_name";
  static final String EXAMINATION_DATE = "examination_date";
  static final String NOTE = "note";

  /**
   * Canonical order of the data columns: the reader reports errors in it. The participant code is
   * not a column: the system generates it.
   */
  static final List<String> ORDERED =
      List.of(
          FULL_NAME,
          DATE_OF_BIRTH,
          SEX,
          IDENTIFICATION_NUMBER,
          IDENTIFICATION_ISSUE_DATE,
          IDENTIFICATION_ISSUE_PLACE,
          ETHNICITY,
          PHONE,
          EMAIL,
          ADDRESS,
          WORKPLACE,
          DEPARTMENT_NAME,
          POSITION_NAME,
          EXAMINATION_DATE,
          NOTE);

  /** Every column the template writes, left to right: the ordinal first, then the data columns. */
  static final List<String> TEMPLATE_COLUMNS =
      Stream.concat(Stream.of(ORDINAL), ORDERED.stream()).toList();

  /** Vietnamese header shown in row 1 of the Participants sheet, per machine key. */
  static final Map<String, String> HEADERS =
      Map.ofEntries(
          Map.entry(ORDINAL, "STT"),
          Map.entry(FULL_NAME, "Họ Và Tên"),
          Map.entry(DATE_OF_BIRTH, "Ngày Sinh"),
          Map.entry(SEX, "Giới Tính"),
          Map.entry(IDENTIFICATION_NUMBER, "CCCD"),
          Map.entry(IDENTIFICATION_ISSUE_DATE, "Ngày Cấp CCCD"),
          Map.entry(IDENTIFICATION_ISSUE_PLACE, "Nơi Cấp CCCD"),
          Map.entry(ETHNICITY, "Dân Tộc"),
          Map.entry(PHONE, "Số Điện Thoại"),
          Map.entry(EMAIL, "Email"),
          Map.entry(ADDRESS, "Chỗ Ở"),
          Map.entry(WORKPLACE, "Nơi Làm Việc"),
          Map.entry(DEPARTMENT_NAME, "Đơn Vị/Phòng Ban"),
          Map.entry(POSITION_NAME, "Chức Vụ"),
          Map.entry(EXAMINATION_DATE, "Ngày Khám"),
          Map.entry(NOTE, "Ghi Chú"));

  private static final Map<String, String> KEY_BY_NORMALIZED_HEADER = new HashMap<>();

  static {
    HEADERS.forEach((key, label) -> KEY_BY_NORMALIZED_HEADER.put(normalize(label), key));
  }

  /** The Vietnamese header written for a machine key. */
  static String header(String key) {
    return HEADERS.get(key);
  }

  /**
   * The machine key of a header cell, or null when the text is not a template header. Matching
   * ignores case, surrounding and repeated spaces and Unicode composition, so "ngày  sinh" and
   * "Ngày Sinh" are the same header; accents are never dropped.
   */
  static String keyOfHeader(String text) {
    return KEY_BY_NORMALIZED_HEADER.get(normalize(text));
  }

  private static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFC)
        .trim()
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  /** Metadata keys in the first column of the Instructions sheet, rows 1 to 3. */
  static final String META_TEMPLATE_VERSION = "templateVersion";
  static final String META_BATCH_ID = "batchId";
  static final String META_BATCH_ROW_VERSION = "batchRowVersion";

  /** Zero-based worksheet row of the first line of the examination date list. */
  static final int DATE_LIST_FIRST_ROW = 14;

  private ParticipantExcelColumns() {}
}
