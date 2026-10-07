package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import static com.ngockhanh.clinic.healthexamination.infrastructure.excel.ParticipantExcelColumns.*;

import com.ngockhanh.clinic.healthexamination.application.port.ParticipantExcelReader;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantImportRow;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantWorkbook;
import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ParticipantImportProperties;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Reads the Excel import contract V1 with Apache POI.
 *
 * <p>The package is checked first without reading any cell: size, the OOXML signature, the entry
 * count and extracted size budgets, and the absence of macros and external links. Cells are then
 * read strictly by type: only text cells are accepted, so formulas, numbers (which would lose a
 * leading zero) and Excel dates are rejected instead of being corrected. Nothing is evaluated, no
 * cached formula value is used, and no file or cell content is logged. The first problem, in a
 * fixed order, is reported with the real worksheet row number and the field name but never the
 * rejected value.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PoiParticipantExcelReader implements ParticipantExcelReader {
  private static final byte[] ZIP_SIGNATURE = {'P', 'K', 3, 4};
  private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final int MAX_HEADER_COLUMNS = 64;
  private static final Set<String> REQUIRED = Set.of(
      FULL_NAME, DATE_OF_BIRTH, SEX, IDENTIFICATION_NUMBER, DEPARTMENT_NAME, POSITION_NAME,
      EXAMINATION_DATE);
  private static final Set<String> DATE_COLUMNS = Set.of(DATE_OF_BIRTH, EXAMINATION_DATE);
  private static final Set<String> UNTRIMMED = Set.of(IDENTIFICATION_NUMBER);

  private final ParticipantImportProperties limits;

  @Override
  public ParticipantWorkbook read(byte[] bytes) {
    preflight(bytes);
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      return parse(workbook);
    } catch (ApplicationException rejected) {
      throw rejected;
    } catch (IOException | RuntimeException unreadable) {
      log.debug("Participant workbook could not be read: cause={}", unreadable.getClass().getSimpleName());
      throw invalid("The file is not a valid XLSX workbook");
    }
  }

  /** Rejects oversized, non-OOXML, macro-enabled, linked or zip-bomb-like packages. */
  private void preflight(byte[] bytes) {
    if (bytes.length > limits.maxFileBytes()) throw new MaxUploadSizeExceededException(limits.maxFileBytes());
    if (bytes.length < ZIP_SIGNATURE.length) throw invalid("The file is not a valid XLSX workbook");
    for (int i = 0; i < ZIP_SIGNATURE.length; i++)
      if (bytes[i] != ZIP_SIGNATURE[i]) throw invalid("The file is not a valid XLSX workbook");

    int entries = 0;
    long total = 0;
    boolean hasContentTypes = false;
    boolean hasWorkbook = false;
    byte[] buffer = new byte[8192];
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        if (++entries > limits.maxZipEntries()) throw invalid("The workbook contains too many parts");
        String name = entry.getName();
        if (name.startsWith("xl/vbaProject")
            || name.startsWith("xl/externalLinks/")
            || name.startsWith("xl/embeddings/"))
          throw invalid("Macros, embedded objects and external links are not supported");
        hasContentTypes |= "[Content_Types].xml".equals(name);
        hasWorkbook |= "xl/workbook.xml".equals(name);
        long entryBytes = 0;
        for (int read = zip.read(buffer); read != -1; read = zip.read(buffer)) {
          entryBytes += read;
          total += read;
          if (entryBytes > limits.maxEntryBytes() || total > limits.maxTotalBytes())
            throw invalid("The workbook is too large once extracted");
        }
      }
    } catch (IOException | RuntimeException unreadable) {
      if (unreadable instanceof ApplicationException rejected) throw rejected;
      throw invalid("The file is not a valid XLSX workbook");
    }
    if (!hasContentTypes || !hasWorkbook) throw invalid("The file is not a valid XLSX workbook");
  }

  private ParticipantWorkbook parse(XSSFWorkbook workbook) {
    Sheet participants = workbook.getSheet(PARTICIPANTS_SHEET);
    if (participants == null) throw invalid("The Participants sheet is missing");
    Sheet instructions = workbook.getSheet(INSTRUCTIONS_SHEET);
    if (instructions == null)
      throw invalid("The Instructions sheet is missing; download the template again");
    for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
      Sheet other = workbook.getSheetAt(index);
      String name = other.getSheetName();
      if (!name.equals(PARTICIPANTS_SHEET)
          && !name.equals(INSTRUCTIONS_SHEET)
          && other.getPhysicalNumberOfRows() > 0)
        throw invalid("The workbook has an unexpected sheet with data");
    }
    if (participants.getNumMergedRegions() > 0)
      throw invalid("Merged cells are not allowed in the Participants sheet");

    int templateVersion = integer(metadata(instructions, 0, META_TEMPLATE_VERSION));
    UUID batchId = uuid(metadata(instructions, 1, META_BATCH_ID));
    long batchRowVersion = longValue(metadata(instructions, 2, META_BATCH_ROW_VERSION));

    Map<String, Integer> columns = headerColumns(participants);
    List<ParticipantImportRow> rows = new ArrayList<>();
    Set<Integer> mappedColumns = Set.copyOf(columns.values());
    for (Row row : participants) {
      if (row.getRowNum() == 0) continue;
      if (isBlank(row, columns, mappedColumns)) continue;
      int excelRow = row.getRowNum() + 1;
      if (row.getRowNum() > limits.maxRows())
        throw invalid("Data is only accepted in worksheet rows 2 to " + (limits.maxRows() + 1));
      rows.add(readRow(row, excelRow, columns));
    }
    if (rows.isEmpty()) throw invalid("The workbook has no data rows");
    return new ParticipantWorkbook(templateVersion, batchId, batchRowVersion, rows);
  }

  private Map<String, Integer> headerColumns(Sheet sheet) {
    Row header = sheet.getRow(0);
    if (header == null) throw invalid("The header row is missing");
    if (header.getLastCellNum() > MAX_HEADER_COLUMNS) throw invalid("The header row has too many columns");
    Map<String, Integer> columns = new HashMap<>();
    for (int column = 0; column < header.getLastCellNum(); column++) {
      Cell cell = header.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
      if (cell == null) continue;
      if (cell.getCellType() != CellType.STRING)
        throw invalid("The header at column " + (column + 1) + " must be a text cell");
      String name = cell.getStringCellValue().trim();
      if (name.isEmpty()) continue;
      if (!ORDERED.contains(name))
        throw invalid("The header at column " + (column + 1) + " is not part of the template");
      if (columns.put(name, column) != null) throw invalid("The header " + name + " appears more than once");
    }
    for (String name : ORDERED)
      if (!columns.containsKey(name)) throw invalid("The header " + name + " is missing");
    return columns;
  }

  private boolean isBlank(Row row, Map<String, Integer> columns, Set<Integer> mappedColumns) {
    boolean blank = true;
    for (Cell cell : row) {
      if (cell.getCellType() == CellType.BLANK) continue;
      if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()) continue;
      if (!mappedColumns.contains(cell.getColumnIndex()))
        throw invalid("Row " + (row.getRowNum() + 1) + ": there is a value outside the template columns");
      blank = false;
    }
    return blank;
  }

  private ParticipantImportRow readRow(Row row, int excelRow, Map<String, Integer> columns) {
    Map<String, String> text = new HashMap<>();
    for (String name : ORDERED) {
      Cell cell = row.getCell(columns.get(name), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
      text.put(name, cellText(cell, name, excelRow));
    }
    for (String name : ORDERED)
      if (REQUIRED.contains(name) && text.get(name) == null)
        throw invalid("Row " + excelRow + ": " + name + " is required");
    return new ParticipantImportRow(
        excelRow,
        text.get(PARTICIPANT_CODE),
        text.get(FULL_NAME),
        date(text.get(DATE_OF_BIRTH), DATE_OF_BIRTH, excelRow),
        text.get(SEX),
        text.get(IDENTIFICATION_NUMBER),
        text.get(PHONE),
        text.get(EMAIL),
        text.get(DEPARTMENT_NAME),
        text.get(POSITION_NAME),
        date(text.get(EXAMINATION_DATE), EXAMINATION_DATE, excelRow));
  }

  /** Returns the text of a text cell, or null when it is empty; every other cell type is rejected. */
  private String cellText(Cell cell, String field, int excelRow) {
    if (cell == null || cell.getCellType() == CellType.BLANK) return null;
    if (cell.getCellType() != CellType.STRING)
      throw invalid("Row " + excelRow + ": " + field + " must be a text cell");
    String value = cell.getStringCellValue();
    if (value.length() > limits.maxCellChars())
      throw invalid("Row " + excelRow + ": " + field + " is too long");
    if (value.isBlank()) return null;
    return UNTRIMMED.contains(field) ? value : value.trim();
  }

  private static LocalDate date(String value, String field, int excelRow) {
    if (value == null || !DATE_COLUMNS.contains(field))
      throw invalid("Row " + excelRow + ": " + field + " is required");
    try {
      if (ISO_DATE.matcher(value).matches()) return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (DateTimeParseException invalidDate) {
      // reported below with the field name only
    }
    throw invalid("Row " + excelRow + ": " + field + " must be a text date in yyyy-MM-dd format");
  }

  private String metadata(Sheet instructions, int rowIndex, String expectedKey) {
    Row row = instructions.getRow(rowIndex);
    Cell key = row == null ? null : row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    Cell value = row == null ? null : row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    if (key == null
        || value == null
        || key.getCellType() != CellType.STRING
        || value.getCellType() != CellType.STRING
        || !expectedKey.equals(key.getStringCellValue().trim()))
      throw invalid("The template information is missing; download the template again");
    return value.getStringCellValue().trim();
  }

  private static int integer(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw invalid("The template information is not valid; download the template again");
    }
  }

  private static long longValue(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw invalid("The template information is not valid; download the template again");
    }
  }

  private static UUID uuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw invalid("The template information is not valid; download the template again");
    }
  }

  private static ApplicationException invalid(String message) {
    return new ApplicationException(ApplicationException.Type.INVALID_INPUT, message);
  }
}
