package com.ngockhanh.clinic.healthexamination.infrastructure.word;

import com.ngockhanh.clinic.healthexamination.application.port.out.PaymentReportDocumentWriter;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentReportDocument;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * Renders the payment summary of a batch as an A4 portrait Word document with Apache POI.
 *
 * <p>Layout: a borderless two-column header (clinic identity on the left, national motto on the
 * right), the title with "(TẠM TÍNH)" while the batch is not finalized, the organization and batch
 * details with the three Participant counts, the items table with a bold total row, the total in
 * words, the calculation note, the place and date line in Asia/Ho_Chi_Minh and two signature
 * blocks. The document holds counts and amounts only; no Participant is named or identified. It is
 * built in memory and never stored.
 */
@Component
@RequiredArgsConstructor
public class PoiPaymentReportDocxWriter implements PaymentReportDocumentWriter {
  static final String TITLE = "BẢNG TỔNG HỢP KHÁM SỨC KHỎE VÀ GIÁ TRỊ THANH TOÁN";
  static final String PROVISIONAL_MARK = "(TẠM TÍNH)";
  static final String CALCULATION_NOTE =
      "Ghi chú: Thành tiền = số người thực tế khám từng hạng mục × đơn giá thỏa thuận.";
  static final String UNKNOWN_SERVICE = "Hạng mục không còn trong danh mục";

  private static final String FONT = "Times New Roman";
  private static final int BODY_SIZE = 12;
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final int PAGE_WIDTH = 11906;
  private static final int PAGE_HEIGHT = 16838;
  private static final int MARGIN = 1134;
  private static final int TEXT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
  private static final int[] ITEM_COLUMNS = {700, 3638, 1300, 1900, 2100};
  private static final String[] ITEM_HEADERS = {
    "STT", "Hạng mục", "Số người khám", "Đơn giá (đ)", "Thành tiền (đ)"
  };

  private final ReportClinicProperties clinic;

  @Override
  public byte[] write(PaymentReportDocument document) {
    try (XWPFDocument doc = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      setUpPage(doc);
      PaymentSummaryReportResponse report = document.report();
      writeHeader(doc);
      writeTitle(doc, report);
      writeInformation(doc, document);
      writeItems(doc, report);
      writeTotalInWords(doc, report);
      textRun(paragraph(doc, ParagraphAlignment.LEFT, 120, 0), CALCULATION_NOTE, false, true);
      writeSignatures(doc, report);
      doc.getProperties().getCoreProperties().setTitle(TITLE);
      if (!clinic.name().isEmpty()) doc.getProperties().getCoreProperties().setCreator(clinic.name());
      doc.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Payment summary document could not be rendered", e);
    }
  }

  private static void setUpPage(XWPFDocument doc) {
    var section = doc.getDocument().getBody().addNewSectPr();
    var size = section.addNewPgSz();
    size.setW(BigInteger.valueOf(PAGE_WIDTH));
    size.setH(BigInteger.valueOf(PAGE_HEIGHT));
    var margin = section.addNewPgMar();
    margin.setTop(BigInteger.valueOf(MARGIN));
    margin.setBottom(BigInteger.valueOf(MARGIN));
    margin.setLeft(BigInteger.valueOf(MARGIN));
    margin.setRight(BigInteger.valueOf(MARGIN));
  }

  private void writeHeader(XWPFDocument doc) {
    XWPFTable table = borderlessTable(doc, 1, 2, new int[] {TEXT_WIDTH / 2, TEXT_WIDTH / 2});
    XWPFTableRow row = table.getRow(0);
    XWPFTableCell left = row.getCell(0);
    first(left, ParagraphAlignment.CENTER);
    textRun(left.getParagraphs().get(0), clinic.name().toUpperCase(Locale.ROOT), true, false);
    if (!clinic.address().isEmpty())
      textRun(cellParagraph(left, ParagraphAlignment.CENTER), clinic.address(), false, false);
    if (!clinic.phone().isEmpty())
      textRun(cellParagraph(left, ParagraphAlignment.CENTER), "Điện thoại: " + clinic.phone(), false, false);
    XWPFTableCell right = row.getCell(1);
    first(right, ParagraphAlignment.CENTER);
    textRun(right.getParagraphs().get(0), "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", true, false, 11);
    textRun(cellParagraph(right, ParagraphAlignment.CENTER), "Độc lập – Tự do – Hạnh phúc", true, false, 12);
  }

  private void writeTitle(XWPFDocument doc, PaymentSummaryReportResponse report) {
    textRun(paragraph(doc, ParagraphAlignment.CENTER, 360, 0), TITLE, true, false, 14);
    if (report.provisional())
      textRun(paragraph(doc, ParagraphAlignment.CENTER, 0, 120), PROVISIONAL_MARK, true, false, 14);
    else paragraph(doc, ParagraphAlignment.CENTER, 0, 120);
  }

  private void writeInformation(XWPFDocument doc, PaymentReportDocument document) {
    PaymentSummaryReportResponse report = document.report();
    labelled(doc, "Đơn vị: ", document.organizationName());
    if (notBlank(document.organizationTaxCode()))
      labelled(doc, "Mã số thuế: ", document.organizationTaxCode());
    if (notBlank(document.organizationAddress()))
      labelled(doc, "Địa chỉ: ", document.organizationAddress());
    labelled(doc, "Đợt khám: ", report.batchName() + " (" + report.batchCode() + ")");
    labelled(doc, "Thời gian: ", period(document.startDate(), document.endDate()));
    labelled(doc, "Địa điểm: ", place(document.siteName(), document.siteAddress()));
    labelled(
        doc,
        "Số người: ",
        "đăng ký "
            + report.registeredCount()
            + " – đã khám "
            + report.attendedCount()
            + " – đã đối soát "
            + report.reconciledCount());
    paragraph(doc, ParagraphAlignment.LEFT, 0, 0);
  }

  private void writeItems(XWPFDocument doc, PaymentSummaryReportResponse report) {
    List<PaymentSummaryReportResponse.Item> items = report.items();
    XWPFTable table = doc.createTable(items.size() + 2, ITEM_HEADERS.length);
    table.setWidth(TEXT_WIDTH);
    table.setTableAlignment(TableRowAlign.CENTER);
    XWPFTableRow head = table.getRow(0);
    for (int c = 0; c < ITEM_HEADERS.length; c++) {
      cell(head.getCell(c), ITEM_COLUMNS[c], ITEM_HEADERS[c], ParagraphAlignment.CENTER, true);
      head.getCell(c).setColor("D9D9D9");
    }
    int number = 1;
    for (PaymentSummaryReportResponse.Item item : items) {
      XWPFTableRow row = table.getRow(number);
      cell(row.getCell(0), ITEM_COLUMNS[0], String.valueOf(number), ParagraphAlignment.CENTER, false);
      cell(row.getCell(1), ITEM_COLUMNS[1], serviceName(item), ParagraphAlignment.LEFT, false);
      cell(row.getCell(2), ITEM_COLUMNS[2], count(item.examinedCount()), ParagraphAlignment.RIGHT, false);
      cell(row.getCell(3), ITEM_COLUMNS[3], money(item.unitPrice()), ParagraphAlignment.RIGHT, false);
      cell(row.getCell(4), ITEM_COLUMNS[4], money(item.amount()), ParagraphAlignment.RIGHT, false);
      number++;
    }
    XWPFTableRow total = table.getRow(items.size() + 1);
    cell(total.getCell(0), ITEM_COLUMNS[0], "", ParagraphAlignment.CENTER, true);
    cell(total.getCell(1), ITEM_COLUMNS[1], "Tổng cộng", ParagraphAlignment.LEFT, true);
    cell(total.getCell(2), ITEM_COLUMNS[2], "", ParagraphAlignment.RIGHT, true);
    cell(total.getCell(3), ITEM_COLUMNS[3], "", ParagraphAlignment.RIGHT, true);
    cell(total.getCell(4), ITEM_COLUMNS[4], money(report.totalAmount()), ParagraphAlignment.RIGHT, true);
  }

  private void writeTotalInWords(XWPFDocument doc, PaymentSummaryReportResponse report) {
    XWPFParagraph paragraph = paragraph(doc, ParagraphAlignment.LEFT, 160, 0);
    textRun(paragraph, "Bằng chữ: ", true, false);
    textRun(paragraph, VietnameseAmountInWords.of(report.totalAmount()), false, true);
  }

  private void writeSignatures(XWPFDocument doc, PaymentSummaryReportResponse report) {
    var generated = report.generatedAt().atZone(BUSINESS_ZONE);
    String date =
        String.format(
            "ngày %02d tháng %02d năm %d",
            generated.getDayOfMonth(), generated.getMonthValue(), generated.getYear());
    textRun(
        paragraph(doc, ParagraphAlignment.RIGHT, 240, 120),
        clinic.city().isEmpty() ? capitalise(date) : clinic.city() + ", " + date,
        false,
        true);
    XWPFTable table = borderlessTable(doc, 1, 2, new int[] {TEXT_WIDTH / 2, TEXT_WIDTH / 2});
    String[] titles = {"ĐẠI DIỆN ĐƠN VỊ", "ĐẠI DIỆN PHÒNG KHÁM"};
    for (int c = 0; c < 2; c++) {
      XWPFTableCell cell = table.getRow(0).getCell(c);
      first(cell, ParagraphAlignment.CENTER);
      textRun(cell.getParagraphs().get(0), titles[c], true, false);
      textRun(cellParagraph(cell, ParagraphAlignment.CENTER), "(Ký, ghi rõ họ tên)", false, true);
      for (int i = 0; i < 4; i++) cellParagraph(cell, ParagraphAlignment.CENTER);
    }
  }

  // --- helpers ---

  private static XWPFTable borderlessTable(XWPFDocument doc, int rows, int cols, int[] widths) {
    XWPFTable table = doc.createTable(rows, cols);
    table.setWidth(TEXT_WIDTH);
    table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
    table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
    table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
    table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
    table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
    table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
    for (XWPFTableRow row : table.getRows())
      for (int c = 0; c < cols; c++) row.getCell(c).setWidth(String.valueOf(widths[c]));
    return table;
  }

  private static void first(XWPFTableCell cell, ParagraphAlignment alignment) {
    cell.getParagraphs().get(0).setAlignment(alignment);
  }

  private static XWPFParagraph cellParagraph(XWPFTableCell cell, ParagraphAlignment alignment) {
    XWPFParagraph paragraph = cell.addParagraph();
    paragraph.setAlignment(alignment);
    return paragraph;
  }

  private static XWPFParagraph paragraph(
      XWPFDocument doc, ParagraphAlignment alignment, int spaceBefore, int spaceAfter) {
    XWPFParagraph paragraph = doc.createParagraph();
    paragraph.setAlignment(alignment);
    paragraph.setSpacingBefore(spaceBefore);
    paragraph.setSpacingAfter(spaceAfter);
    return paragraph;
  }

  private static void labelled(XWPFDocument doc, String label, String value) {
    XWPFParagraph paragraph = paragraph(doc, ParagraphAlignment.LEFT, 0, 40);
    textRun(paragraph, label, true, false);
    textRun(paragraph, value == null ? "" : value, false, false);
  }

  private static void cell(
      XWPFTableCell cell, int width, String text, ParagraphAlignment alignment, boolean bold) {
    cell.setWidth(String.valueOf(width));
    XWPFParagraph paragraph = cell.getParagraphs().get(0);
    paragraph.setAlignment(alignment);
    textRun(paragraph, text, bold, false);
  }

  private static XWPFRun textRun(XWPFParagraph paragraph, String text, boolean bold, boolean italic) {
    return textRun(paragraph, text, bold, italic, BODY_SIZE);
  }

  private static XWPFRun textRun(
      XWPFParagraph paragraph, String text, boolean bold, boolean italic, int size) {
    XWPFRun run = paragraph.createRun();
    run.setFontFamily(FONT);
    run.setFontSize(size);
    run.setBold(bold);
    run.setItalic(italic);
    run.setText(text);
    return run;
  }

  private static String serviceName(PaymentSummaryReportResponse.Item item) {
    if (notBlank(item.serviceName())) return item.serviceName();
    if (notBlank(item.serviceCode())) return item.serviceCode();
    return UNKNOWN_SERVICE;
  }

  private static String period(LocalDate start, LocalDate end) {
    if (start == null) return "";
    if (end == null || end.equals(start)) return start.format(DATE);
    return start.format(DATE) + " – " + end.format(DATE);
  }

  private static String place(String name, String address) {
    if (!notBlank(address)) return name == null ? "" : name;
    return notBlank(name) ? name + ", " + address : address;
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static String capitalise(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static String count(long value) {
    return new DecimalFormat("#,##0", symbols()).format(value);
  }

  /** Vietnamese grouping with dots; two decimals only when the amount has a fractional part. */
  static String money(BigDecimal value) {
    boolean fractional = value.stripTrailingZeros().scale() > 0;
    return new DecimalFormat(fractional ? "#,##0.00" : "#,##0", symbols()).format(value);
  }

  private static DecimalFormatSymbols symbols() {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
    symbols.setGroupingSeparator('.');
    symbols.setDecimalSeparator(',');
    return symbols;
  }
}
