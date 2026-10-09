package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.application.query.ParticipantImportRow;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantTemplateData;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantWorkbook;
import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ParticipantImportProperties;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class PoiParticipantExcelTest {
  private static final UUID BATCH_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final List<LocalDate> DAYS =
      List.of(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5));

  private final ParticipantImportProperties limits =
      new ParticipantImportProperties(null, null, null, null, null, null);
  private final PoiParticipantTemplateWriter writer = new PoiParticipantTemplateWriter();
  private final PoiParticipantExcelReader reader = new PoiParticipantExcelReader(limits);

  private byte[] template() {
    return writer.write(new ParticipantTemplateData(BATCH_ID, 7L, DAYS));
  }

  private byte[] filled(Consumer<Sheet> fill) {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
      fill.accept(workbook.getSheet("Participants"));
      return toBytes(workbook);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] toBytes(XSSFWorkbook workbook) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    workbook.write(out);
    return out.toByteArray();
  }

  private static void text(Sheet sheet, int row, String... values) {
    Row r = sheet.getRow(row) == null ? sheet.createRow(row) : sheet.getRow(row);
    for (int i = 0; i < values.length; i++)
      if (values[i] != null) r.createCell(i).setCellValue(values[i]);
  }

  /**
   * Columns: STT, name, birth, sex, CCCD, issue date/place, ethnicity, phone, email, address,
   * workplace, department, position, examination date, note.
   */
  private static void validRow(Sheet sheet, int row, String identification) {
    text(
        sheet,
        row,
        "1",
        "Nguyen Van A",
        "1990-01-31",
        "MALE",
        identification,
        null,
        null,
        null,
        "0900000000",
        null,
        null,
        null,
        "Ke toan",
        "Nhan vien",
        "2026-10-04",
        null);
  }

  private String rejection(byte[] bytes) {
    return assertThatThrownBy(() -> reader.read(bytes))
        .isInstanceOf(ApplicationException.class)
        .actual()
        .getMessage();
  }

  @Test
  void templateCarriesMetadataHeadersDatesAndDropdowns() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
      assertThat(workbook.getSheetName(0)).isEqualTo("Participants");
      assertThat(workbook.getSheetName(1)).isEqualTo("Instructions");
      Sheet participants = workbook.getSheet("Participants");
      for (int i = 0; i < ParticipantExcelColumns.TEMPLATE_COLUMNS.size(); i++)
        assertThat(participants.getRow(0).getCell(i).getStringCellValue())
            .isEqualTo(
                ParticipantExcelColumns.header(ParticipantExcelColumns.TEMPLATE_COLUMNS.get(i)));
      assertThat(participants.getRow(0).getCell(0).getStringCellValue()).isEqualTo("STT");
      assertThat(participants.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Ngày Sinh");
      assertThat(participants.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Giới Tính");
      assertThat(participants.getRow(0).getLastCellNum()).isEqualTo((short) 16);
      var headers = new java.util.ArrayList<String>();
      participants.getRow(0).forEach(cell -> headers.add(cell.getStringCellValue()));
      assertThat(headers)
          .contains("Ngày Cấp CCCD", "Nơi Cấp CCCD", "Dân Tộc", "Chỗ Ở", "Nơi Làm Việc", "Ghi Chú")
          .doesNotContain("Mã Người Khám");
      assertThat(participants.getDataValidations()).hasSizeGreaterThanOrEqualTo(2);
      Sheet instructions = workbook.getSheet("Instructions");
      assertThat(instructions.getRow(1).getCell(1).getStringCellValue())
          .isEqualTo(BATCH_ID.toString());
      assertThat(instructions.getRow(2).getCell(1).getStringCellValue()).isEqualTo("7");
      assertThat(instructions.getRow(14).getCell(0).getStringCellValue()).isEqualTo("2026-10-04");
      assertThat(instructions.getRow(15).getCell(0).getStringCellValue()).isEqualTo("2026-10-05");
      assertThat(workbook.getName("ExaminationDates")).isNotNull();
    }
  }

  @Test
  void emptyTemplateIsRejectedBecauseItHasNoDataRows() {
    assertThat(rejection(template())).isEqualTo("The workbook has no data rows");
  }

  @Test
  void roundTripKeepsTextExactlyAndRealRowNumbers() {
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "0012345678");
              validRow(sheet, 4, "0099999999"); // rows 3 and 4 stay blank on purpose
            });

    ParticipantWorkbook parsed = reader.read(bytes);

    assertThat(parsed.templateVersion()).isEqualTo(ParticipantWorkbook.TEMPLATE_VERSION);
    assertThat(parsed.templateBatchId()).isEqualTo(BATCH_ID);
    assertThat(parsed.templateBatchVersion()).isEqualTo(7L);
    assertThat(parsed.rows()).extracting(ParticipantImportRow::rowNumber).containsExactly(2, 5);
    ParticipantImportRow first = parsed.rows().get(0);
    assertThat(first.identificationNumber()).isEqualTo("0012345678");
    assertThat(first.email()).isNull();
    assertThat(first.identificationIssueDate()).isNull();
    assertThat(first.ethnicity()).isNull();
    assertThat(first.note()).isNull();
    assertThat(first.dateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 31));
    assertThat(first.examinationDate()).isEqualTo(LocalDate.of(2026, 10, 4));
    assertThat(first.fullName()).isEqualTo("Nguyen Van A");
  }

  @Test
  void optionalPersonalDetailsRoundTripAndBlankOnesStayNull() {
    ParticipantWorkbook parsed =
        reader.read(
            filled(
                sheet -> {
                  validRow(sheet, 1, "123");
                  text(
                      sheet,
                      2,
                      "2",
                      "Tran Thi B",
                      "1985-12-01",
                      "FEMALE",
                      "456",
                      "2020-05-20",
                      " Cuc CSQLHC ",
                      "Kinh",
                      null,
                      "b@example.test",
                      "12 Tran Hung Dao",
                      "Cong ty ABC",
                      "Ke toan",
                      "Truong phong",
                      "2026-10-05",
                      "Di ung phan");
                }));

    ParticipantImportRow second = parsed.rows().get(1);
    assertThat(second.identificationIssueDate()).isEqualTo(LocalDate.of(2020, 5, 20));
    assertThat(second.identificationIssuePlace()).isEqualTo("Cuc CSQLHC");
    assertThat(second.ethnicity()).isEqualTo("Kinh");
    assertThat(second.phone()).isNull();
    assertThat(second.email()).isEqualTo("b@example.test");
    assertThat(second.address()).isEqualTo("12 Tran Hung Dao");
    assertThat(second.workplace()).isEqualTo("Cong ty ABC");
    assertThat(second.note()).isEqualTo("Di ung phan");
    ParticipantImportRow first = parsed.rows().get(0);
    assertThat(first.address()).isNull();
    assertThat(first.workplace()).isNull();
  }

  @Test
  void acceptsAddressAtTheSupportedLength() {
    String address = "a".repeat(1000);
    ParticipantWorkbook parsed =
        reader.read(
            filled(
                sheet -> {
                  validRow(sheet, 1, "123");
                  sheet.getRow(1).createCell(10).setCellValue(address);
                }));

    assertThat(parsed.rows().getFirst().address()).isEqualTo(address);
  }

  @Test
  void acceptsNoteAtTheSupportedLength() {
    String note = "n".repeat(2000);
    ParticipantWorkbook parsed =
        reader.read(
            filled(
                sheet -> {
                  validRow(sheet, 1, "123");
                  sheet.getRow(1).createCell(15).setCellValue(note);
                }));

    assertThat(parsed.rows().getFirst().note()).isEqualTo(note);
  }

  @Test
  void rejectsNoteBeyondTheSupportedLengthWithALargerCellBudget() {
    PoiParticipantExcelReader largerBudget =
        new PoiParticipantExcelReader(
            new ParticipantImportProperties(null, null, 4000, null, null, null));
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "123");
              sheet.getRow(1).createCell(15).setCellValue("n".repeat(2001));
            });

    assertThatThrownBy(() -> largerBudget.read(bytes))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Row 2: note is too long");
  }

  @Test
  void rejectsAddressBeyondTheSupportedLength() {
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "123");
              sheet.getRow(1).createCell(10).setCellValue("a".repeat(1001));
            });

    assertThat(rejection(bytes)).isEqualTo("Row 2: address is too long");
  }

  @Test
  void preservesTheLengthLimitOfOtherTextFields() {
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "123");
              sheet.getRow(1).createCell(11).setCellValue("w".repeat(501));
            });

    assertThat(rejection(bytes)).isEqualTo("Row 2: workplace is too long");
  }

  @Test
  void issueDateMustBeATextIsoDate() {
    assertThat(
            rejection(
                filled(
                    sheet -> {
                      validRow(sheet, 1, "123");
                      sheet.getRow(1).createCell(5).setCellValue("15/03/2021");
                    })))
        .isEqualTo("Row 2: identification_issue_date must be a text date in yyyy-MM-dd format");
  }

  @Test
  void ordinalColumnIsIgnoredWhateverItHolds() {
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "123");
              sheet.getRow(1).getCell(0).setBlank();
              sheet.getRow(1).getCell(0).setCellValue(99.0); // a number, never read
              text(sheet, 2, "2"); // only a row number: counts as a blank row
              sheet.getRow(2).getCell(0).setBlank();
              sheet.getRow(2).getCell(0).setCellFormula("1+1"); // a formula there is not evaluated
            });
    ParticipantWorkbook parsed = reader.read(bytes);
    assertThat(parsed.rows()).extracting(ParticipantImportRow::rowNumber).containsExactly(2);
  }

  @Test
  void aSheetWithOnlyOrdinalNumbersHasNoDataRows() {
    assertThat(rejection(filled(sheet -> text(sheet, 1, "1"))))
        .isEqualTo("The workbook has no data rows");
  }

  @Test
  void ordinalHeaderIsOptional() {
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "123");
              sheet.getRow(0).removeCell(sheet.getRow(0).getCell(0));
              sheet.getRow(1).removeCell(sheet.getRow(1).getCell(0));
            });
    assertThat(reader.read(bytes).rows()).hasSize(1);
  }

  @Test
  void theOldParticipantCodeHeaderIsNotPartOfTheTemplateAnymore() {
    assertThat(
            rejection(
                filled(sheet -> sheet.getRow(0).createCell(16).setCellValue("Mã Người Khám"))))
        .isEqualTo("The header at column 17 is not part of the template");
  }

  @Test
  void identificationNumberIsNeverTrimmed() {
    ParticipantWorkbook parsed = reader.read(filled(sheet -> validRow(sheet, 1, " 123 ")));
    assertThat(parsed.rows().get(0).identificationNumber()).isEqualTo(" 123 ");
  }

  @Test
  void numericCellIsRejectedWithoutEchoingTheValue() {
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "0012345678");
              sheet.getRow(1).getCell(4).setCellValue(12345678.0);
            });
    String message = rejection(bytes);
    assertThat(message).isEqualTo("Row 2: identification_number must be a text cell");
    assertThat(message).doesNotContain("12345678");
  }

  @Test
  void formulaCellIsRejected() {
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "0012345678");
              sheet.getRow(1).getCell(1).setCellFormula("\"x\"&\"y\"");
            });
    assertThat(rejection(bytes)).isEqualTo("Row 2: full_name must be a text cell");
  }

  @Test
  void invalidAndNonIsoDatesAreRejected() {
    String nonIso =
        rejection(
            filled(
                sheet -> {
                  validRow(sheet, 1, "123");
                  sheet.getRow(1).getCell(2).setCellValue("31/01/1990");
                }));
    assertThat(nonIso).isEqualTo("Row 2: date_of_birth must be a text date in yyyy-MM-dd format");
    String impossible =
        rejection(
            filled(
                sheet -> {
                  validRow(sheet, 1, "123");
                  sheet.getRow(1).getCell(14).setCellValue("2026-02-30");
                }));
    assertThat(impossible)
        .isEqualTo("Row 2: examination_date must be a text date in yyyy-MM-dd format");
  }

  @Test
  void missingRequiredFieldNamesRowAndField() {
    String message =
        rejection(
            filled(
                sheet -> {
                  validRow(sheet, 1, "123");
                  sheet.getRow(1).removeCell(sheet.getRow(1).getCell(13));
                }));
    assertThat(message).isEqualTo("Row 2: position_name is required");
  }

  @Test
  void overlongCellIsRejected() {
    PoiParticipantExcelReader strict =
        new PoiParticipantExcelReader(
            new ParticipantImportProperties(null, null, 5L > 0 ? 10 : 0, null, null, null));
    byte[] bytes = filled(sheet -> validRow(sheet, 1, "123"));
    assertThatThrownBy(() -> strict.read(bytes))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Row 2: full_name is too long");
  }

  @Test
  void dataBeyondRowLimitIsRejected() {
    PoiParticipantExcelReader small =
        new PoiParticipantExcelReader(
            new ParticipantImportProperties(null, 2, null, null, null, null));
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "1");
              validRow(sheet, 2, "2");
              validRow(sheet, 3, "3");
            });
    assertThatThrownBy(() -> small.read(bytes))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("Data is only accepted in worksheet rows 2 to 3");
  }

  @Test
  void headerProblemsAreReported() {
    assertThat(rejection(filled(sheet -> sheet.getRow(0).getCell(3).setCellValue("gender"))))
        .isEqualTo("The header at column 4 is not part of the template");
    assertThat(rejection(filled(sheet -> sheet.getRow(0).getCell(3).setCellValue("Họ Và Tên"))))
        .isEqualTo("The header Họ Và Tên appears more than once");
    assertThat(rejection(filled(sheet -> sheet.getRow(0).removeCell(sheet.getRow(0).getCell(3)))))
        .isEqualTo("The header Giới Tính is missing");
  }

  @Test
  void englishMachineKeysAreNoLongerAcceptedAsHeaders() {
    assertThat(rejection(filled(sheet -> sheet.getRow(0).getCell(2).setCellValue("date_of_birth"))))
        .isEqualTo("The header at column 3 is not part of the template");
  }

  @Test
  void headerMatchingIgnoresCaseAndExtraSpacesButNotAccents() {
    byte[] relaxed =
        filled(
            sheet -> {
              validRow(sheet, 1, "123");
              sheet.getRow(0).getCell(2).setCellValue("  ngày   sinh ");
              sheet.getRow(0).getCell(3).setCellValue("GIỚI TÍNH");
            });
    ParticipantImportRow row = reader.read(relaxed).rows().get(0);
    assertThat(row.dateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 31));
    assertThat(row.sex()).isEqualTo("MALE");

    assertThat(rejection(filled(sheet -> sheet.getRow(0).getCell(2).setCellValue("Ngay Sinh"))))
        .isEqualTo("The header at column 3 is not part of the template");
  }

  @Test
  void columnOrderIsFree() {
    byte[] bytes =
        filled(
            sheet -> {
              validRow(sheet, 1, "123");
              Row header = sheet.getRow(0);
              Row data = sheet.getRow(1);
              // swap full_name and sex columns, header and data together
              String hFull = header.getCell(1).getStringCellValue();
              header.getCell(1).setCellValue(header.getCell(3).getStringCellValue());
              header.getCell(3).setCellValue(hFull);
              String dFull = data.getCell(1).getStringCellValue();
              data.getCell(1).setCellValue(data.getCell(3).getStringCellValue());
              data.getCell(3).setCellValue(dFull);
            });
    ParticipantImportRow row = reader.read(bytes).rows().get(0);
    assertThat(row.fullName()).isEqualTo("Nguyen Van A");
    assertThat(row.sex()).isEqualTo("MALE");
  }

  @Test
  void valueOutsideTemplateColumnsIsRejected() {
    String message =
        rejection(
            filled(
                sheet -> {
                  validRow(sheet, 1, "123");
                  sheet.getRow(1).createCell(20).setCellValue("stray");
                }));
    assertThat(message).isEqualTo("Row 2: there is a value outside the template columns");
  }

  @Test
  void mergedCellsAreRejected() {
    String message =
        rejection(
            filled(
                sheet -> {
                  validRow(sheet, 1, "123");
                  sheet.addMergedRegion(new CellRangeAddress(5, 5, 0, 1));
                }));
    assertThat(message).isEqualTo("Merged cells are not allowed in the Participants sheet");
  }

  @Test
  void unexpectedSheetWithDataIsRejected() {
    byte[] bytes;
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(filled(s -> validRow(s, 1, "1"))))) {
      workbook.createSheet("Extra").createRow(0).createCell(0).setCellValue("x");
      bytes = toBytes(workbook);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    assertThat(rejection(bytes)).isEqualTo("The workbook has an unexpected sheet with data");
  }

  @Test
  void missingOrTamperedMetadataAsksForANewTemplate() {
    byte[] bytes;
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(filled(s -> validRow(s, 1, "1"))))) {
      workbook.getSheet("Instructions").getRow(1).getCell(1).setCellValue("not-a-uuid");
      bytes = toBytes(workbook);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    assertThat(rejection(bytes))
        .isEqualTo("The template information is not valid; download the template again");
  }

  @Test
  void notAWorkbookIsRejectedWithoutDetails() {
    assertThat(rejection("hello".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo("The file is not a valid XLSX workbook");
    assertThat(rejection(new byte[0])).isEqualTo("The file is not a valid XLSX workbook");
    assertThat(rejection(new byte[] {'P', 'K', 3, 4, 0, 0, 0}))
        .isEqualTo("The file is not a valid XLSX workbook");
  }

  @Test
  void oversizedFileRaisesMaxUploadSize() {
    PoiParticipantExcelReader tiny =
        new PoiParticipantExcelReader(
            new ParticipantImportProperties(10L, null, null, null, null, null));
    assertThatThrownBy(() -> tiny.read(template()))
        .isInstanceOf(MaxUploadSizeExceededException.class);
  }

  @Test
  void macroExternalLinkAndEmbeddingPartsAreRejected() throws IOException {
    for (String part :
        List.of("xl/vbaProject.bin", "xl/externalLinks/externalLink1.xml", "xl/embeddings/o.bin")) {
      assertThat(rejection(withExtraPart(template(), part, new byte[] {1})))
          .isEqualTo("Macros, embedded objects and external links are not supported");
    }
  }

  @Test
  void tooManyPartsAndZipBombsAreRejected() throws IOException {
    PoiParticipantExcelReader fewParts =
        new PoiParticipantExcelReader(
            new ParticipantImportProperties(null, null, null, 3, null, null));
    assertThatThrownBy(() -> fewParts.read(template()))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("The workbook contains too many parts");

    byte[] bomb = withExtraPart(template(), "xl/media/bomb.bin", new byte[2_000_000]);
    PoiParticipantExcelReader smallEntries =
        new PoiParticipantExcelReader(
            new ParticipantImportProperties(null, null, null, null, 1_000_000L, null));
    assertThatThrownBy(() -> smallEntries.read(bomb))
        .isInstanceOf(ApplicationException.class)
        .hasMessage("The workbook is too large once extracted");
  }

  private static byte[] withExtraPart(byte[] original, String name, byte[] content)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (java.util.zip.ZipInputStream in =
            new java.util.zip.ZipInputStream(new ByteArrayInputStream(original));
        ZipOutputStream zip = new ZipOutputStream(out)) {
      for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
        zip.putNextEntry(new ZipEntry(e.getName()));
        in.transferTo(zip);
        zip.closeEntry();
      }
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content);
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  @Test
  void blankHeaderCellTypeIsStillChecked() {
    String message =
        rejection(
            filled(
                sheet -> {
                  Cell cell = sheet.getRow(0).getCell(3);
                  cell.setCellValue(1.0);
                  assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
                }));
    assertThat(message).isEqualTo("The header at column 4 must be a text cell");
  }
}
