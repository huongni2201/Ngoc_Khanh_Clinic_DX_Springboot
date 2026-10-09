package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import static com.ngockhanh.clinic.healthexamination.infrastructure.excel.ParticipantExcelColumns.*;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantTemplateWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantTemplateData;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantWorkbook;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Renders the Excel import template V1 with Apache POI.
 *
 * <p>The {@code Participants} sheet starts with an {@code STT} row-number column (ignored on
 * import) followed by the Vietnamese data headers, every input column formatted as
 * text so leading zeros survive, a frozen header, a dropdown for the sex column and a dropdown for
 * the examination date column fed by a named range on the {@code Instructions} sheet. The {@code
 * Instructions} sheet holds the template version, the batch identifier and the batch configuration
 * version, Vietnamese guidance and the valid dates. The template has no sample row, formula, macro
 * or external link, and every value is written as plain text.
 */
@Component
public class PoiParticipantTemplateWriter implements ParticipantTemplateWriter {
  /** Last worksheet row (zero-based) that carries input formatting and dropdowns: row 1001. */
  private static final int LAST_INPUT_ROW = 1_000;

  private static final int[] COLUMN_WIDTH_CHARS = {6, 28, 14, 12, 18, 16, 24, 14, 16, 26, 36, 28, 26, 22, 14, 30};

  private static final String[] GUIDANCE = {
    "Nhập mỗi người khám trên một dòng của sheet Participants, bắt đầu từ dòng 2. Không sửa dòng tiêu đề. Cột STT chỉ để đánh số và bị bỏ qua khi nhập.",
    "Mọi ô là văn bản. Giữ nguyên số 0 ở đầu của cột CCCD và Số Điện Thoại.",
    "Ngày Sinh, Ngày Cấp CCCD và Ngày Khám nhập dạng văn bản yyyy-MM-dd, ví dụ 1990-01-31.",
    "Ngày Khám phải là một trong các ngày khám của đợt, liệt kê bên dưới.",
    "Giới Tính chọn một trong MALE, FEMALE, OTHER, UNKNOWN.",
    "CCCD gồm 1 đến 20 chữ số và không được trùng trong file hay trong đợt khám.",
    "Bắt buộc: Họ Và Tên, Ngày Sinh, Giới Tính, CCCD, Đơn Vị/Phòng Ban, Chức Vụ, Ngày Khám. Các cột còn lại là tùy chọn. Mã người khám do hệ thống tự sinh.",
    "Chỉ thêm mới: file có bất kỳ lỗi nào thì toàn bộ file bị từ chối và không có dòng nào được lưu.",
  };

  @Override
  public byte[] write(ParticipantTemplateData data) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      CellStyle text = workbook.createCellStyle();
      text.setDataFormat(workbook.createDataFormat().getFormat("@"));
      CellStyle header = workbook.createCellStyle();
      header.cloneStyleFrom(text);
      Font bold = workbook.createFont();
      bold.setBold(true);
      header.setFont(bold);

      Sheet participants = workbook.createSheet(PARTICIPANTS_SHEET);
      Sheet instructions = workbook.createSheet(INSTRUCTIONS_SHEET);
      writeParticipantsSheet(participants, text, header);
      writeInstructionsSheet(instructions, text, header, data);
      defineDateList(workbook, data);
      addDropdowns(participants);

      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Participant import template could not be rendered", e);
    }
  }

  private static void writeParticipantsSheet(Sheet sheet, CellStyle text, CellStyle header) {
    Row headerRow = sheet.createRow(0);
    for (int column = 0; column < TEMPLATE_COLUMNS.size(); column++) {
      var cell = headerRow.createCell(column);
      cell.setCellStyle(header);
      cell.setCellValue(header(TEMPLATE_COLUMNS.get(column)));
      sheet.setDefaultColumnStyle(column, text);
      sheet.setColumnWidth(column, COLUMN_WIDTH_CHARS[column] * 256);
    }
    sheet.createFreezePane(0, 1);
  }

  private static void writeInstructionsSheet(
      Sheet sheet, CellStyle text, CellStyle header, ParticipantTemplateData data) {
    sheet.setDefaultColumnStyle(0, text);
    sheet.setDefaultColumnStyle(1, text);
    sheet.setColumnWidth(0, 60 * 256);
    sheet.setColumnWidth(1, 40 * 256);
    metadata(sheet, 0, META_TEMPLATE_VERSION, Integer.toString(ParticipantWorkbook.TEMPLATE_VERSION), text);
    metadata(sheet, 1, META_BATCH_ID, data.batchId().toString(), text);
    metadata(sheet, 2, META_BATCH_ROW_VERSION, Long.toString(data.batchRowVersion()), text);

    var title = sheet.createRow(4).createCell(0);
    title.setCellStyle(header);
    title.setCellValue("Hướng dẫn nhập danh sách người khám");
    for (int line = 0; line < GUIDANCE.length; line++) {
      var cell = sheet.createRow(5 + line).createCell(0);
      cell.setCellStyle(text);
      cell.setCellValue(GUIDANCE[line]);
    }
    var datesTitle = sheet.createRow(DATE_LIST_FIRST_ROW - 1).createCell(0);
    datesTitle.setCellStyle(header);
    datesTitle.setCellValue("Ngày khám hợp lệ (cột Ngày Khám)");
    int rowIndex = DATE_LIST_FIRST_ROW;
    for (LocalDate date : data.examinationDates()) {
      var cell = sheet.createRow(rowIndex++).createCell(0);
      cell.setCellStyle(text);
      cell.setCellValue(date.toString());
    }
  }

  private static void metadata(Sheet sheet, int rowIndex, String key, String value, CellStyle text) {
    Row row = sheet.createRow(rowIndex);
    var keyCell = row.createCell(0);
    keyCell.setCellStyle(text);
    keyCell.setCellValue(key);
    var valueCell = row.createCell(1);
    valueCell.setCellStyle(text);
    valueCell.setCellValue(value);
  }

  private static void defineDateList(XSSFWorkbook workbook, ParticipantTemplateData data) {
    Name name = workbook.createName();
    name.setNameName(EXAMINATION_DATES_NAME);
    int first = DATE_LIST_FIRST_ROW + 1;
    int last = DATE_LIST_FIRST_ROW + Math.max(1, data.examinationDates().size());
    name.setRefersToFormula(INSTRUCTIONS_SHEET + "!$A$" + first + ":$A$" + last);
  }

  private static void addDropdowns(Sheet sheet) {
    DataValidationHelper helper = sheet.getDataValidationHelper();
    dropdown(
        sheet,
        helper,
        helper.createExplicitListConstraint(Roster.SEX_VALUES.toArray(String[]::new)),
        TEMPLATE_COLUMNS.indexOf(SEX));
    dropdown(
        sheet,
        helper,
        helper.createFormulaListConstraint(EXAMINATION_DATES_NAME),
        TEMPLATE_COLUMNS.indexOf(EXAMINATION_DATE));
  }

  private static void dropdown(
      Sheet sheet, DataValidationHelper helper, DataValidationConstraint constraint, int column) {
    DataValidation validation =
        helper.createValidation(constraint, new CellRangeAddressList(1, LAST_INPUT_ROW, column, column));
    validation.setShowErrorBox(true);
    validation.setSuppressDropDownArrow(true);
    sheet.addValidationData(validation);
  }
}
