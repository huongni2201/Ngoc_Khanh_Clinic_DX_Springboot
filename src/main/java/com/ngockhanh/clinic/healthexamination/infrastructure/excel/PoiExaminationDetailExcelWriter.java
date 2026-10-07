package com.ngockhanh.clinic.healthexamination.infrastructure.excel;

import static com.ngockhanh.clinic.healthexamination.infrastructure.excel.ExaminationDetailExcelColumns.*;

import com.ngockhanh.clinic.healthexamination.application.port.ExaminationDetailExcelWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailExportData;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailWorkbook;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationServiceColumn;
import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ExaminationDetailImportProperties;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Renders the examination detail workbook V1 with Apache POI. The same file is the export and the
 * template of the import.
 *
 * <p>The {@code ChiTietKham} sheet has Vietnamese labels in row 1, hidden machine keys in row 2 and
 * one row per active Participant from row 3. Hidden columns A and B carry the Participant
 * identifier and row version; the identification number is written exactly as given, so the
 * caller must hand over the masked value, and no phone, email or attendance note is written. Every cell is plain text so no value is converted by Excel. Only the
 * actual examination date and the service cells are unlocked, the services have an {@code X}
 * dropdown, and the sheet is protected without a password to help the user; the backend validates
 * everything it reads again. The {@code HuongDan} sheet holds the template version, the batch
 * identifier, the export time and Vietnamese guidance. No formula, macro or external link is
 * written.
 */
@Component
public class PoiExaminationDetailExcelWriter implements ExaminationDetailExcelWriter {
  private static final int[] FIXED_COLUMN_WIDTH_CHARS = {38, 10, 6, 16, 28, 14, 10, 18, 24, 24, 18, 18};
  private static final int SERVICE_COLUMN_WIDTH_CHARS = 18;

  private static final String[] GUIDANCE = {
    "Mỗi dòng của sheet ChiTietKham là một người khám đang hoạt động của đợt khám.",
    "Chỉ chỉnh các ô \"Ngày khám thực tế\" và các cột hạng mục; các ô khác bị khóa và chỉ để đọc.",
    "Đánh dấu X vào hạng mục đã thực hiện. Để trống nếu chưa thực hiện.",
    "File là trạng thái đối soát mới của từng người có trong file: ô trống nghĩa là chưa thực hiện.",
    "Người khám không có trong file được giữ nguyên.",
    "Ngày khám thực tế nhập dạng văn bản yyyy-MM-dd. Để trống để giữ ngày đã ghi hoặc dùng ngày khám dự kiến (chỉ khi ngày dự kiến không nằm trong tương lai).",
    "Không thêm, xóa hay đổi thứ tự cột và không sửa dòng khóa ẩn. Nếu đợt khám thay đổi, hãy xuất lại file.",
    "Có bất kỳ dòng lỗi nào thì toàn bộ file bị từ chối và không có gì được lưu.",
  };

  private final ExaminationDetailImportProperties limits;

  public PoiExaminationDetailExcelWriter(ExaminationDetailImportProperties limits) {
    this.limits = limits;
  }

  /**
   * Renders the workbook.
   *
   * @throws ApplicationException of type {@code INVALID_INPUT} when the batch has more rows than
   *     the import accepts, because the file could not be imported again
   */
  @Override
  public byte[] write(ExaminationDetailExportData data) {
    if (data.rows().size() > limits.maxRows())
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT,
          "The batch has "
              + data.rows().size()
              + " participants, more than the "
              + limits.maxRows()
              + " rows the examination detail import accepts; the file would not be importable");
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      CellStyle text = workbook.createCellStyle();
      text.setDataFormat(workbook.createDataFormat().getFormat("@"));
      CellStyle input = workbook.createCellStyle();
      input.cloneStyleFrom(text);
      input.setLocked(false);
      CellStyle header = workbook.createCellStyle();
      header.cloneStyleFrom(text);
      Font bold = workbook.createFont();
      bold.setBold(true);
      header.setFont(bold);

      XSSFSheet detail = workbook.createSheet(DETAIL_SHEET);
      Sheet guide = workbook.createSheet(GUIDE_SHEET);
      writeDetailSheet(detail, text, input, header, data);
      writeGuideSheet(guide, text, header, data);

      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Examination detail workbook could not be rendered", e);
    }
  }

  private static void writeDetailSheet(
      XSSFSheet sheet,
      CellStyle text,
      CellStyle input,
      CellStyle header,
      ExaminationDetailExportData data) {
    List<ExaminationServiceColumn> services = data.services();
    int lastColumn = FIRST_SERVICE_COLUMN + services.size() - 1;

    Row labels = sheet.createRow(LABEL_ROW);
    Row keys = sheet.createRow(KEY_ROW);
    for (int column = 0; column < FIXED_KEYS.size(); column++) {
      put(labels, column, FIXED_LABELS.get(column), header);
      put(keys, column, FIXED_KEYS.get(column), header);
      sheet.setColumnWidth(column, FIXED_COLUMN_WIDTH_CHARS[column] * 256);
    }
    for (int index = 0; index < services.size(); index++) {
      int column = FIRST_SERVICE_COLUMN + index;
      ExaminationServiceColumn service = services.get(index);
      put(labels, column, service.label() == null ? "" : service.label(), header);
      put(keys, column, serviceKey(service.batchServiceId()), header);
      sheet.setColumnWidth(column, SERVICE_COLUMN_WIDTH_CHARS * 256);
    }
    keys.setZeroHeight(true);
    sheet.setColumnHidden(FIXED_KEYS.indexOf(PARTICIPANT_ID), true);
    sheet.setColumnHidden(FIXED_KEYS.indexOf(ROW_VERSION), true);

    int rowIndex = FIRST_DATA_ROW;
    int sequence = 1;
    for (ExaminationDetailRow row : data.rows()) {
      Row excelRow = sheet.createRow(rowIndex++);
      Set<UUID> performed = new HashSet<>(row.performedBatchServiceIds());
      put(excelRow, FIXED_KEYS.indexOf(PARTICIPANT_ID), row.id().toString(), text);
      put(excelRow, FIXED_KEYS.indexOf(ROW_VERSION), Long.toString(row.rowVersion()), text);
      put(excelRow, FIXED_KEYS.indexOf(SEQ), Integer.toString(sequence++), text);
      put(excelRow, FIXED_KEYS.indexOf(PARTICIPANT_CODE), blankIfNull(row.participantCode()), text);
      put(excelRow, FIXED_KEYS.indexOf(FULL_NAME), row.fullName(), text);
      put(excelRow, FIXED_KEYS.indexOf(DATE_OF_BIRTH), row.dateOfBirth().toString(), text);
      put(excelRow, FIXED_KEYS.indexOf(SEX), row.sex(), text);
      put(excelRow, FIXED_KEYS.indexOf(IDENTIFICATION_NUMBER), row.identificationNumber(), text);
      put(excelRow, FIXED_KEYS.indexOf(DEPARTMENT_NAME), row.departmentName(), text);
      put(excelRow, FIXED_KEYS.indexOf(POSITION_NAME), row.positionName(), text);
      put(excelRow, FIXED_KEYS.indexOf(EXAMINATION_DATE), row.examinationDate().toString(), text);
      put(
          excelRow,
          ACTUAL_DATE_COLUMN,
          row.actualExaminationDate() == null ? "" : row.actualExaminationDate().toString(),
          input);
      for (int index = 0; index < services.size(); index++) {
        String mark = performed.contains(services.get(index).batchServiceId()) ? PERFORMED_MARK : "";
        put(excelRow, FIRST_SERVICE_COLUMN + index, mark, input);
      }
    }

    sheet.createFreezePane(FROZEN_COLUMNS, FIRST_DATA_ROW);
    if (!data.rows().isEmpty() && !services.isEmpty()) {
      DataValidationHelper helper = sheet.getDataValidationHelper();
      DataValidation validation =
          helper.createValidation(
              helper.createExplicitListConstraint(new String[] {PERFORMED_MARK}),
              new CellRangeAddressList(
                  FIRST_DATA_ROW, FIRST_DATA_ROW + data.rows().size() - 1, FIRST_SERVICE_COLUMN, lastColumn));
      validation.setShowErrorBox(true);
      validation.setSuppressDropDownArrow(true);
      sheet.addValidationData(validation);
    }
    sheet.enableLocking();
  }

  private static void writeGuideSheet(
      Sheet sheet, CellStyle text, CellStyle header, ExaminationDetailExportData data) {
    sheet.setDefaultColumnStyle(0, text);
    sheet.setDefaultColumnStyle(1, text);
    sheet.setColumnWidth(0, 60 * 256);
    sheet.setColumnWidth(1, 40 * 256);
    metadata(
        sheet,
        0,
        META_TEMPLATE_VERSION,
        Integer.toString(ExaminationDetailWorkbook.TEMPLATE_VERSION),
        text);
    metadata(sheet, 1, META_BATCH_ID, data.batchId().toString(), text);
    metadata(sheet, 2, META_EXPORTED_AT, data.exportedAt().toString(), text);

    put(sheet.createRow(4), 0, "Hướng dẫn nhập chi tiết khám", header);
    for (int line = 0; line < GUIDANCE.length; line++)
      put(sheet.createRow(5 + line), 0, GUIDANCE[line], text);
  }

  private static void metadata(Sheet sheet, int rowIndex, String key, String value, CellStyle text) {
    Row row = sheet.createRow(rowIndex);
    put(row, 0, key, text);
    put(row, 1, value, text);
  }

  private static void put(Row row, int column, String value, CellStyle style) {
    Cell cell = row.createCell(column);
    cell.setCellStyle(style);
    cell.setCellValue(value);
  }

  private static String blankIfNull(String value) {
    return value == null ? "" : value;
  }
}
