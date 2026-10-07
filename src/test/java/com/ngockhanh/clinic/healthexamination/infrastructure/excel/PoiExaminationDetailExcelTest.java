package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailExportData;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailWorkbook;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationServiceColumn;
import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ExaminationDetailImportProperties;
import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ParticipantImportProperties;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Examination detail workbook V1: what the writer produces and what the reader accepts. */
class PoiExaminationDetailExcelTest {
  private static final UUID BATCH_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID SERVICE_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID SERVICE_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000022");
  private static final int A = ExaminationDetailExcelColumns.FIRST_SERVICE_COLUMN;
  private static final int B = A + 1;
  private static final int DATE = ExaminationDetailExcelColumns.ACTUAL_DATE_COLUMN;
  private static final int FIRST = ExaminationDetailExcelColumns.FIRST_DATA_ROW;

  private final ParticipantImportProperties fileLimits =
      new ParticipantImportProperties(null, null, null, null, null, null);
  private final PoiExaminationDetailExcelWriter writer = new PoiExaminationDetailExcelWriter();
  private final PoiExaminationDetailExcelReader reader =
      new PoiExaminationDetailExcelReader(
          fileLimits, new ExaminationDetailImportProperties(null, null));

  private static ExaminationDetailRow row(
      UUID id, String name, LocalDate actual, long version, UUID... performed) {
    return new ExaminationDetailRow(
        id,
        "NV-" + name,
        name,
        LocalDate.of(1990, 1, 31),
        "MALE",
        "*****6789",
        "Accounting",
        "Staff",
        LocalDate.of(2026, 10, 4),
        actual == null ? "UNCONFIRMED" : "ATTENDED",
        actual,
        "PENDING",
        List.of(performed),
        version);
  }

  private static ExaminationDetailExportData data(ExaminationDetailRow... rows) {
    return new ExaminationDetailExportData(
        BATCH_ID,
        "B1",
        Instant.parse("2026-10-07T00:00:00Z"),
        List.of(
            new ExaminationServiceColumn(SERVICE_A, "Khám nội"),
            new ExaminationServiceColumn(SERVICE_B, "Xét nghiệm")),
        List.of(rows));
  }

  private byte[] export() {
    return writer.write(
        data(
            row(P1, "Person One", LocalDate.of(2026, 10, 5), 4, SERVICE_A),
            row(P2, "Person Two", null, 7)));
  }

  private byte[] edited(byte[] source, Consumer<XSSFWorkbook> change) {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      change.accept(workbook);
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private byte[] editedSheet(Consumer<Sheet> change) {
    return edited(export(), workbook -> change.accept(workbook.getSheet("ChiTietKham")));
  }

  private static void set(Sheet sheet, int row, int column, String value) {
    Row r = sheet.getRow(row) == null ? sheet.createRow(row) : sheet.getRow(row);
    Cell cell = r.getCell(column) == null ? r.createCell(column) : r.getCell(column);
    cell.setCellValue(value);
  }

  private void assertRejected(byte[] file, String... messageParts) {
    assertThatThrownBy(() -> reader.read(file))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT))
        .hasMessageContainingAll(messageParts);
  }

  // --- writer ---

  @Test
  void writesTheTwoSheetsLabelsHiddenKeysAndOneRowPerParticipant() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export()))) {
      assertThat(workbook.getSheetName(0)).isEqualTo("ChiTietKham");
      assertThat(workbook.getSheetName(1)).isEqualTo("HuongDan");
      XSSFSheet sheet = workbook.getSheet("ChiTietKham");
      assertThat(sheet.getRow(0).getCell(A).getStringCellValue()).isEqualTo("Khám nội");
      assertThat(sheet.getRow(0).getCell(B).getStringCellValue()).isEqualTo("Xét nghiệm");
      assertThat(sheet.getRow(1).getCell(A).getStringCellValue()).isEqualTo("svc:" + SERVICE_A);
      assertThat(sheet.getRow(1).getZeroHeight()).isTrue();
      assertThat(sheet.isColumnHidden(0)).isTrue();
      assertThat(sheet.isColumnHidden(1)).isTrue();
      assertThat(sheet.getRow(FIRST).getCell(0).getStringCellValue()).isEqualTo(P1.toString());
      assertThat(sheet.getRow(FIRST).getCell(1).getStringCellValue()).isEqualTo("4");
      assertThat(sheet.getRow(FIRST).getCell(A).getStringCellValue()).isEqualTo("X");
      assertThat(sheet.getRow(FIRST).getCell(B).getStringCellValue()).isEmpty();
      assertThat(sheet.getRow(FIRST).getCell(DATE).getStringCellValue()).isEqualTo("2026-10-05");
      assertThat(sheet.getRow(FIRST + 1).getCell(DATE).getStringCellValue()).isEmpty();
      assertThat(sheet.getLastRowNum()).isEqualTo(FIRST + 1);
      assertThat(sheet.getPaneInformation()).isNotNull();
    }
  }

  @Test
  void everyCellIsTextAndNoFormulaIsWritten() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export()))) {
      for (Sheet sheet : workbook)
        for (Row row : sheet)
          for (Cell cell : row)
            assertThat(cell.getCellType()).as(sheet.getSheetName() + "!" + cell.getAddress())
                .isIn(CellType.STRING, CellType.BLANK);
    }
  }

  @Test
  void onlyTheActualDateAndServiceCellsAreUnlockedAndTheSheetIsProtected() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export()))) {
      XSSFSheet sheet = workbook.getSheet("ChiTietKham");
      assertThat(sheet.getProtect()).isTrue();
      Row data = sheet.getRow(FIRST);
      for (int column = 0; column < DATE; column++)
        assertThat(data.getCell(column).getCellStyle().getLocked()).as("column " + column).isTrue();
      assertThat(data.getCell(DATE).getCellStyle().getLocked()).isFalse();
      assertThat(data.getCell(A).getCellStyle().getLocked()).isFalse();
      assertThat(data.getCell(B).getCellStyle().getLocked()).isFalse();
    }
  }

  @Test
  void servicesHaveAnXDropdown() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export()))) {
      var validations = workbook.getSheet("ChiTietKham").getDataValidations();
      assertThat(validations).hasSize(1);
      var constraint = validations.get(0).getValidationConstraint();
      assertThat(constraint.getExplicitListValues()).containsExactly("X");
      assertThat(validations.get(0).getRegions().getCellRangeAddress(0))
          .isEqualTo(new CellRangeAddress(FIRST, FIRST + 1, A, B));
    }
  }

  @Test
  void writesTheMaskedIdentificationAsGivenAndNoContactOrNoteColumns() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export()))) {
      XSSFSheet sheet = workbook.getSheet("ChiTietKham");
      int identification = ExaminationDetailExcelColumns.FIXED_KEYS.indexOf("identification_number");
      assertThat(sheet.getRow(FIRST).getCell(identification).getStringCellValue()).isEqualTo("*****6789");
      for (Cell key : sheet.getRow(1))
        assertThat(key.getStringCellValue()).doesNotContain("phone", "email", "note");
    }
  }

  @Test
  void theGuideSheetCarriesTheTemplateVersionBatchAndExportTime() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export()))) {
      Sheet guide = workbook.getSheet("HuongDan");
      assertThat(guide.getRow(0).getCell(0).getStringCellValue()).isEqualTo("templateVersion");
      assertThat(guide.getRow(0).getCell(1).getStringCellValue()).isEqualTo("1");
      assertThat(guide.getRow(1).getCell(1).getStringCellValue()).isEqualTo(BATCH_ID.toString());
      assertThat(guide.getRow(2).getCell(1).getStringCellValue()).isEqualTo("2026-10-07T00:00:00Z");
    }
  }

  // --- reader ---

  @Test
  void readsBackWhatTheWriterExported() {
    ExaminationDetailWorkbook read = reader.read(export());

    assertThat(read.templateVersion()).isEqualTo(1);
    assertThat(read.declaredServiceIds()).containsExactlyInAnyOrder(SERVICE_A, SERVICE_B);
    assertThat(read.rows()).hasSize(2);
    ExaminationDetailImportRow first = read.rows().get(0);
    assertThat(first.rowNumber()).isEqualTo(3);
    assertThat(first.participantId()).isEqualTo(P1);
    assertThat(first.rowVersion()).isEqualTo(4);
    assertThat(first.actualExaminationDate()).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(first.performedServiceIds()).containsExactly(SERVICE_A);
    ExaminationDetailImportRow second = read.rows().get(1);
    assertThat(second.rowNumber()).isEqualTo(4);
    assertThat(second.rowVersion()).isEqualTo(7);
    assertThat(second.actualExaminationDate()).isNull();
    assertThat(second.performedServiceIds()).isEmpty();
  }

  @Test
  void acceptsLowercaseXWhitespaceAndFullyBlankRowsInTheMiddle() {
    byte[] file =
        editedSheet(
            sheet -> {
              set(sheet, FIRST, A, "");
              set(sheet, FIRST, B, " x ");
              set(sheet, FIRST + 2, 0, "");
            });

    var read = reader.read(file);

    assertThat(read.rows().get(0).performedServiceIds()).containsExactly(SERVICE_B);
    assertThat(read.rows()).hasSize(2);
  }

  @Test
  void rejectsAnythingButXOrBlankInAServiceCellWithoutEchoingTheValue() {
    byte[] file = editedSheet(sheet -> set(sheet, FIRST + 1, B, "SECRET-VALUE"));

    assertThatThrownBy(() -> reader.read(file))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContainingAll("Row 4", "Xét nghiệm", "X or blank")
        .hasMessageNotContaining("SECRET-VALUE");
  }

  @Test
  void rejectsFormulaAndNumericServiceCells() {
    assertRejected(
        editedSheet(sheet -> sheet.getRow(FIRST).getCell(A).setCellFormula("1+1")),
        "Row 3",
        "X or blank");
    assertRejected(
        editedSheet(sheet -> sheet.getRow(FIRST).getCell(B).setCellValue(1d)),
        "Row 3",
        "X or blank");
  }

  @Test
  void rejectsNumericOrMalformedActualDates() {
    assertRejected(
        editedSheet(sheet -> sheet.getRow(FIRST).getCell(DATE).setCellValue(46300d)),
        "Row 3",
        "actual_examination_date");
    assertRejected(
        editedSheet(sheet -> set(sheet, FIRST, DATE, "2026-13-45")),
        "Row 3",
        "actual_examination_date");
    assertRejected(
        editedSheet(sheet -> set(sheet, FIRST, DATE, "05/10/2026")),
        "Row 3",
        "yyyy-MM-dd");
  }

  @Test
  void rejectsMissingOrInvalidIdentifiersAndVersions() {
    assertRejected(editedSheet(sheet -> set(sheet, FIRST, 0, "")), "Row 3", "participant_id");
    assertRejected(editedSheet(sheet -> set(sheet, FIRST, 0, "not-a-uuid")), "Row 3", "participant_id");
    assertRejected(editedSheet(sheet -> set(sheet, FIRST, 1, "")), "Row 3", "row_version");
    assertRejected(editedSheet(sheet -> set(sheet, FIRST, 1, "-1")), "Row 3", "row_version");
    assertRejected(editedSheet(sheet -> set(sheet, FIRST, 1, "abc")), "Row 3", "row_version");
  }

  @Test
  void rejectsADuplicateParticipant() {
    assertRejected(
        editedSheet(sheet -> set(sheet, FIRST + 1, 0, P1.toString())),
        "Row 4",
        "more than once",
        "row 3");
  }

  @Test
  void rejectsAChangedKeyRowOrExtraServiceKeys() {
    assertRejected(editedSheet(sheet -> set(sheet, 1, 0, "participant")), "layout");
    assertRejected(editedSheet(sheet -> set(sheet, 1, A, "svc:not-an-id")), "layout");
    assertRejected(editedSheet(sheet -> set(sheet, 1, B, "svc:" + SERVICE_A)), "more than once");
    assertRejected(editedSheet(sheet -> set(sheet, 1, B, "other:" + SERVICE_B)), "layout");
  }

  @Test
  void rejectsAValueOutsideTheDeclaredColumns() {
    assertRejected(editedSheet(sheet -> set(sheet, FIRST, B + 1, "X")), "Row 3", "outside");
  }

  @Test
  void rejectsMissingSheetsMergedCellsAndForeignSheetsWithData() {
    assertRejected(
        edited(export(), workbook -> workbook.removeSheetAt(1)), "HuongDan");
    assertRejected(
        edited(export(), workbook -> workbook.removeSheetAt(0)), "ChiTietKham");
    assertRejected(
        editedSheet(sheet -> sheet.addMergedRegion(new CellRangeAddress(FIRST, FIRST, 3, 4))),
        "Merged");
    assertRejected(
        edited(export(), workbook -> set(workbook.createSheet("Other"), 0, 0, "data")),
        "unexpected sheet");
  }

  @Test
  void rejectsAnOlderOrUnreadableTemplateVersion() {
    assertRejected(
        edited(export(), workbook -> set(workbook.getSheet("HuongDan"), 0, 1, "2")), "older template");
    assertRejected(
        edited(export(), workbook -> set(workbook.getSheet("HuongDan"), 0, 1, "x")), "not valid");
    assertRejected(
        edited(export(), workbook -> workbook.getSheet("HuongDan").removeRow(workbook.getSheet("HuongDan").getRow(0))),
        "template information");
  }

  @Test
  void rejectsAWorkbookWithoutDataRows() {
    byte[] file = writer.write(data());
    assertRejected(file, "no data rows");
  }

  @Test
  void rejectsMoreRowsThanTheConfiguredLimit() {
    var small =
        new PoiExaminationDetailExcelReader(
            fileLimits, new ExaminationDetailImportProperties(1, 100));
    assertThatThrownBy(() -> small.read(export()))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("rows 3 to 3");
  }

  @Test
  void rejectsMoreServiceColumnsThanTheConfiguredLimit() {
    var narrow =
        new PoiExaminationDetailExcelReader(
            fileLimits, new ExaminationDetailImportProperties(2000, 1));
    assertThatThrownBy(() -> narrow.read(export()))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("too many service columns");
  }

  @Test
  void rejectsAFileThatIsNotAnXlsxWorkbook() {
    assertRejected(new byte[] {1, 2, 3, 4}, "XLSX");
    assertThatThrownBy(() -> reader.read(new byte[0])).isInstanceOf(ApplicationException.class);
  }

  @Test
  void exportThenImportOfAnUntouchedFileCarriesTheStoredStateUnchanged() {
    var read = reader.read(export());

    // Re-exporting the stored state and reading it again yields the same typed rows.
    var again =
        reader.read(
            writer.write(
                data(
                    row(P1, "Person One", LocalDate.of(2026, 10, 5), 4, SERVICE_A),
                    row(P2, "Person Two", null, 7))));
    assertThat(again.rows()).isEqualTo(read.rows());
    assertThat(again.declaredServiceIds()).isEqualTo(Set.copyOf(read.declaredServiceIds()));
  }
}
