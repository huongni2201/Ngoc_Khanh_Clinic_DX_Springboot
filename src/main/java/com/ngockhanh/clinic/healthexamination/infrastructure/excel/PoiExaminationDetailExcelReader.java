package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import static com.ngockhanh.clinic.healthexamination.infrastructure.excel.ExaminationDetailExcelColumns.*;

import com.ngockhanh.clinic.healthexamination.application.port.ExaminationDetailExcelReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailWorkbook;
import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ExaminationDetailImportProperties;
import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ParticipantImportProperties;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Reads the examination detail Excel contract V1 with Apache POI.
 *
 * <p>The package is checked first by the shared {@link OoxmlPackageGuard}. Cells are then read
 * strictly by type: a service cell is {@code X}, {@code x} or blank and nothing else, and the actual
 * examination date is an ISO text date, so formulas, numbers and Excel dates are rejected instead of
 * being corrected. The informational columns are never read. Nothing is evaluated, no cached
 * formula value is used and no file or cell content is logged. The first problem, in a fixed order,
 * is reported with the real worksheet row number and the field name but never the rejected value.
 * The reader never touches the database, so it cannot tell whether the declared services belong to
 * a batch; that comparison belongs to the caller.
 */
@Slf4j
@Component
public class PoiExaminationDetailExcelReader implements ExaminationDetailExcelReader {
  private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final int MAX_LABEL_CHARS = 80;
  private static final String LAYOUT_MISMATCH = "The file layout is not valid; export it again";

  private final ParticipantImportProperties fileLimits;
  private final ExaminationDetailImportProperties detailLimits;
  private final OoxmlPackageGuard guard;

  public PoiExaminationDetailExcelReader(
      ParticipantImportProperties fileLimits, ExaminationDetailImportProperties detailLimits) {
    this.fileLimits = fileLimits;
    this.detailLimits = detailLimits;
    this.guard = new OoxmlPackageGuard(fileLimits);
  }

  @Override
  public ExaminationDetailWorkbook read(byte[] bytes) {
    guard.check(bytes);
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      return parse(workbook);
    } catch (ApplicationException rejected) {
      throw rejected;
    } catch (IOException | RuntimeException unreadable) {
      log.debug(
          "Examination detail workbook could not be read: cause={}",
          unreadable.getClass().getSimpleName());
      throw invalid("The file is not a valid XLSX workbook");
    }
  }

  private ExaminationDetailWorkbook parse(XSSFWorkbook workbook) {
    Sheet detail = workbook.getSheet(DETAIL_SHEET);
    if (detail == null) throw invalid("The " + DETAIL_SHEET + " sheet is missing; export the file again");
    Sheet guide = workbook.getSheet(GUIDE_SHEET);
    if (guide == null) throw invalid("The " + GUIDE_SHEET + " sheet is missing; export the file again");
    for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
      Sheet other = workbook.getSheetAt(index);
      String name = other.getSheetName();
      if (!name.equals(DETAIL_SHEET) && !name.equals(GUIDE_SHEET) && other.getPhysicalNumberOfRows() > 0)
        throw invalid("The workbook has an unexpected sheet with data");
    }
    if (detail.getNumMergedRegions() > 0)
      throw invalid("Merged cells are not allowed in the " + DETAIL_SHEET + " sheet");

    int templateVersion = templateVersion(guide);
    if (templateVersion != ExaminationDetailWorkbook.TEMPLATE_VERSION)
      throw invalid("The file was made with an older template; export it again");

    List<UUID> serviceIds = serviceKeys(detail);
    int lastColumn = FIRST_SERVICE_COLUMN + serviceIds.size();
    Row labelRow = detail.getRow(LABEL_ROW);

    List<ExaminationDetailImportRow> rows = new ArrayList<>();
    Map<UUID, Integer> firstRowOfParticipant = new HashMap<>();
    for (Row row : detail) {
      if (row.getRowNum() < FIRST_DATA_ROW) continue;
      if (isBlank(row, lastColumn)) continue;
      int excelRow = row.getRowNum() + 1;
      if (row.getRowNum() - FIRST_DATA_ROW >= detailLimits.maxRows())
        throw invalid(
            "Data is only accepted in worksheet rows "
                + (FIRST_DATA_ROW + 1)
                + " to "
                + (FIRST_DATA_ROW + detailLimits.maxRows()));
      ExaminationDetailImportRow parsed = readRow(row, excelRow, serviceIds, labelRow);
      Integer first = firstRowOfParticipant.putIfAbsent(parsed.participantId(), excelRow);
      if (first != null)
        throw invalid(
            "Row " + excelRow + ": participant appears more than once (first at row " + first + ")");
      rows.add(parsed);
    }
    if (rows.isEmpty()) throw invalid("The workbook has no data rows");
    return new ExaminationDetailWorkbook(templateVersion, new LinkedHashSet<>(serviceIds), rows);
  }

  /** Checks the fixed keys of row 2 and returns the declared service keys in column order. */
  private List<UUID> serviceKeys(Sheet detail) {
    Row keyRow = detail.getRow(KEY_ROW);
    if (keyRow == null) throw invalid(LAYOUT_MISMATCH);
    int lastKeyColumn = keyRow.getLastCellNum();
    if (lastKeyColumn < FIRST_SERVICE_COLUMN) throw invalid(LAYOUT_MISMATCH);
    if (lastKeyColumn > FIRST_SERVICE_COLUMN + detailLimits.maxServiceColumns())
      throw invalid("The file has too many service columns");
    for (int column = 0; column < FIRST_SERVICE_COLUMN; column++)
      if (!FIXED_KEYS.get(column).equals(keyText(keyRow, column))) throw invalid(LAYOUT_MISMATCH);

    List<UUID> serviceIds = new ArrayList<>();
    Set<UUID> seen = new HashSet<>();
    for (int column = FIRST_SERVICE_COLUMN; column < lastKeyColumn; column++) {
      String key = keyText(keyRow, column);
      if (key == null || !key.startsWith(SERVICE_KEY_PREFIX)) throw invalid(LAYOUT_MISMATCH);
      UUID serviceId;
      try {
        serviceId = UUID.fromString(key.substring(SERVICE_KEY_PREFIX.length()));
      } catch (IllegalArgumentException notAnId) {
        throw invalid(LAYOUT_MISMATCH);
      }
      if (!seen.add(serviceId)) throw invalid("A service column appears more than once");
      serviceIds.add(serviceId);
    }
    return serviceIds;
  }

  private static String keyText(Row keyRow, int column) {
    Cell cell = keyRow.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    if (cell == null || cell.getCellType() != CellType.STRING) return null;
    return cell.getStringCellValue().trim();
  }

  private int templateVersion(Sheet guide) {
    Row row = guide.getRow(0);
    Cell key = row == null ? null : row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    Cell value = row == null ? null : row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    if (key == null
        || value == null
        || key.getCellType() != CellType.STRING
        || value.getCellType() != CellType.STRING
        || !META_TEMPLATE_VERSION.equals(key.getStringCellValue().trim()))
      throw invalid("The template information is missing; export the file again");
    try {
      return Integer.parseInt(value.getStringCellValue().trim());
    } catch (NumberFormatException notANumber) {
      throw invalid("The template information is not valid; export the file again");
    }
  }

  /** A row is blank when every cell is empty; a value outside the declared columns is rejected. */
  private boolean isBlank(Row row, int lastColumn) {
    boolean blank = true;
    for (Cell cell : row) {
      if (cell.getCellType() == CellType.BLANK) continue;
      if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()) continue;
      if (cell.getColumnIndex() >= lastColumn)
        throw invalid("Row " + (row.getRowNum() + 1) + ": there is a value outside the file columns");
      blank = false;
    }
    return blank;
  }

  private ExaminationDetailImportRow readRow(
      Row row, int excelRow, List<UUID> serviceIds, Row labelRow) {
    UUID participantId = participantId(row, excelRow);
    long rowVersion = rowVersion(row, excelRow);
    LocalDate actualDate = actualDate(row, excelRow);
    Set<UUID> performed = new LinkedHashSet<>();
    for (int index = 0; index < serviceIds.size(); index++) {
      int column = FIRST_SERVICE_COLUMN + index;
      Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
      if (cell == null) continue;
      if (cell.getCellType() != CellType.STRING)
        throw invalid("Row " + excelRow + ": column \"" + label(labelRow, column) + "\" accepts only X or blank");
      String value = cell.getStringCellValue().trim();
      if (value.isEmpty()) continue;
      if (!value.equalsIgnoreCase(PERFORMED_MARK))
        throw invalid("Row " + excelRow + ": column \"" + label(labelRow, column) + "\" accepts only X or blank");
      performed.add(serviceIds.get(index));
    }
    return new ExaminationDetailImportRow(excelRow, participantId, rowVersion, actualDate, performed);
  }

  private UUID participantId(Row row, int excelRow) {
    String text = text(row, FIXED_KEYS.indexOf(PARTICIPANT_ID), PARTICIPANT_ID, excelRow);
    if (text == null) throw invalid("Row " + excelRow + ": " + PARTICIPANT_ID + " is required");
    try {
      return UUID.fromString(text);
    } catch (IllegalArgumentException notAnId) {
      throw invalid("Row " + excelRow + ": " + PARTICIPANT_ID + " is not valid");
    }
  }

  private long rowVersion(Row row, int excelRow) {
    int column = FIXED_KEYS.indexOf(ROW_VERSION);
    Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    if (cell == null) throw invalid("Row " + excelRow + ": " + ROW_VERSION + " is required");
    long version;
    if (cell.getCellType() == CellType.NUMERIC) {
      double number = cell.getNumericCellValue();
      if (number != Math.rint(number) || number < 0 || number > Long.MAX_VALUE)
        throw invalid("Row " + excelRow + ": " + ROW_VERSION + " is not valid");
      version = (long) number;
    } else {
      String text = text(row, column, ROW_VERSION, excelRow);
      if (text == null) throw invalid("Row " + excelRow + ": " + ROW_VERSION + " is required");
      try {
        version = Long.parseLong(text);
      } catch (NumberFormatException notANumber) {
        throw invalid("Row " + excelRow + ": " + ROW_VERSION + " is not valid");
      }
      if (version < 0) throw invalid("Row " + excelRow + ": " + ROW_VERSION + " is not valid");
    }
    return version;
  }

  private LocalDate actualDate(Row row, int excelRow) {
    String text = text(row, ACTUAL_DATE_COLUMN, ACTUAL_EXAMINATION_DATE, excelRow);
    if (text == null) return null;
    try {
      if (ISO_DATE.matcher(text).matches()) return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (DateTimeParseException invalidDate) {
      // reported below with the field name only
    }
    throw invalid(
        "Row " + excelRow + ": " + ACTUAL_EXAMINATION_DATE + " must be a text date in yyyy-MM-dd format");
  }

  /** Returns the trimmed text of a text cell, or null when it is empty; other types are rejected. */
  private String text(Row row, int column, String field, int excelRow) {
    Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    if (cell == null) return null;
    if (cell.getCellType() != CellType.STRING)
      throw invalid("Row " + excelRow + ": " + field + " must be a text cell");
    String value = cell.getStringCellValue();
    if (value.length() > fileLimits.maxCellChars())
      throw invalid("Row " + excelRow + ": " + field + " is too long");
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** Display label of a service column for error messages; never a value of the data rows. */
  private static String label(Row labelRow, int column) {
    Cell cell = labelRow == null ? null : labelRow.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    String label = cell == null || cell.getCellType() != CellType.STRING ? "" : cell.getStringCellValue();
    label = label.replaceAll("\\p{Cntrl}", " ").trim();
    if (label.isEmpty()) return "#" + (column + 1);
    return label.length() > MAX_LABEL_CHARS ? label.substring(0, MAX_LABEL_CHARS) : label;
  }

  private static ApplicationException invalid(String message) {
    return new ApplicationException(ApplicationException.Type.INVALID_INPUT, message);
  }
}
