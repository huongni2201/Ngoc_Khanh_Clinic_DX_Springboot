package com.ngockhanh.clinic.healthexamination.infrastructure.word;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.healthexamination.application.query.PaymentReportDocument;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

/** Generates a real document and reopens it, which also proves the POI lite schemas are enough. */
class PoiPaymentReportDocxWriterTest {
  private static final Instant GENERATED_AT = Instant.parse("2026-10-07T18:30:00Z");

  private final PoiPaymentReportDocxWriter writer =
      new PoiPaymentReportDocxWriter(
          new ReportClinicProperties("Phòng khám Test", "1 Đường Thử", "0123 456 789", "Hà Nội"));

  private static PaymentSummaryReportResponse.Item item(
      String name, String price, long count) {
    BigDecimal unit = new BigDecimal(price);
    return PaymentSummaryReportResponse.Item.builder()
        .batchServiceId(UUID.randomUUID())
        .serviceCode("C-" + name)
        .serviceName(name)
        .displayOrder(1)
        .unitPrice(unit)
        .examinedCount(count)
        .amount(unit.multiply(BigDecimal.valueOf(count)))
        .build();
  }

  private static PaymentReportDocument document(boolean provisional, String total) {
    var report =
        PaymentSummaryReportResponse.builder()
            .batchId(UUID.randomUUID())
            .batchCode("DK-01")
            .batchName("Khám định kỳ 2026")
            .batchStatus(provisional ? "READY" : "FINALIZED")
            .provisional(provisional)
            .registeredCount(120)
            .attendedCount(100)
            .reconciledCount(90)
            .items(
                List.of(
                    item("Khám nội", "150000", 100),
                    item("Xét nghiệm máu", "1005000", 0),
                    item("Siêu âm", "250000.50", 2)))
            .totalAmount(new BigDecimal(total))
            .generatedAt(GENERATED_AT)
            .build();
    return new PaymentReportDocument(
        report,
        "Công ty ABC",
        "0101234567",
        "9 Phố Mẫu",
        LocalDate.of(2026, 10, 4),
        LocalDate.of(2026, 10, 5),
        "Phòng khám",
        "2 Đường Khám");
  }

  private static List<String> texts(XWPFDocument doc) {
    List<String> out = new ArrayList<>();
    doc.getParagraphs().forEach(p -> out.add(p.getText()));
    for (XWPFTable table : doc.getTables())
      for (XWPFTableRow row : table.getRows())
        row.getTableCells()
            .forEach(c -> c.getParagraphs().forEach(p -> out.add(p.getText())));
    return out;
  }

  private static XWPFDocument reopen(byte[] bytes) throws IOException {
    return new XWPFDocument(new ByteArrayInputStream(bytes));
  }

  @Test
  void writesAReadableA4PortraitDocumentWithAllSections() throws IOException {
    byte[] bytes = writer.write(document(true, "15500001"));

    try (XWPFDocument doc = reopen(bytes)) {
      var size = doc.getDocument().getBody().getSectPr().getPgSz();
      assertThat(size.getW().toString()).isEqualTo("11906");
      assertThat(size.getH().toString()).isEqualTo("16838");
      List<String> text = texts(doc);
      assertThat(text)
          .contains(
              "BẢNG TỔNG HỢP KHÁM SỨC KHỎE VÀ GIÁ TRỊ THANH TOÁN",
              "(TẠM TÍNH)",
              "PHÒNG KHÁM TEST",
              "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM",
              "Độc lập – Tự do – Hạnh phúc",
              "Đơn vị: Công ty ABC",
              "Mã số thuế: 0101234567",
              "Đợt khám: Khám định kỳ 2026 (DK-01)",
              "Thời gian: 04/10/2026 – 05/10/2026",
              "Địa điểm: Phòng khám, 2 Đường Khám",
              "Số người: đăng ký 120 – đã khám 100 – đã đối soát 90",
              "Khám nội",
              "Xét nghiệm máu",
              "Siêu âm",
              "150.000",
              "15.000.000",
              "250.000,50",
              "500.001",
              "Tổng cộng",
              "ĐẠI DIỆN ĐƠN VỊ",
              "ĐẠI DIỆN PHÒNG KHÁM",
              "(Ký, ghi rõ họ tên)",
              "Hà Nội, ngày 08 tháng 10 năm 2026");
      assertThat(text).anyMatch(t -> t.startsWith("Bằng chữ: Mười lăm triệu"));
      assertThat(text).anyMatch(t -> t.startsWith("Ghi chú: Thành tiền = số người"));
    }
  }

  @Test
  void itemsTableHasHeaderOneRowPerItemAndABoldTotalRow() throws IOException {
    try (XWPFDocument doc = reopen(writer.write(document(true, "15500001")))) {
      XWPFTable items = doc.getTables().get(1);
      assertThat(items.getRows()).hasSize(5);
      assertThat(items.getRow(0).getTableCells().stream().map(c -> c.getText()).toList())
          .containsExactly("STT", "Hạng mục", "Số người khám", "Đơn giá (đ)", "Thành tiền (đ)");
      assertThat(items.getRow(1).getCell(0).getText()).isEqualTo("1");
      assertThat(items.getRow(3).getCell(2).getText()).isEqualTo("2");
      var totalRow = items.getRow(4);
      assertThat(totalRow.getCell(1).getText()).isEqualTo("Tổng cộng");
      assertThat(totalRow.getCell(4).getText()).isEqualTo("15.500.001");
      assertThat(totalRow.getCell(1).getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
    }
  }

  @Test
  void headerAndSignatureTablesAreBorderless() throws IOException {
    try (XWPFDocument doc = reopen(writer.write(document(false, "1005000")))) {
      assertThat(doc.getTables()).hasSize(3);
      for (int index : new int[] {0, 2}) {
        var borders = doc.getTables().get(index).getCTTbl().getTblPr().getTblBorders();
        assertThat(borders.getTop().getVal().toString()).isEqualTo("none");
        assertThat(borders.getInsideV().getVal().toString()).isEqualTo("none");
      }
    }
  }

  @Test
  void omitsProvisionalMarkOnceTheBatchIsFinalized() throws IOException {
    try (XWPFDocument doc = reopen(writer.write(document(false, "1005000")))) {
      assertThat(texts(doc)).doesNotContain("(TẠM TÍNH)");
      assertThat(texts(doc))
          .anyMatch(t -> t.equals("Bằng chữ: Một triệu không trăm linh năm nghìn đồng chẵn"));
    }
  }

  @Test
  void leavesOutBlankClinicDetailsAndUsesTheCatalogFallbackName() throws IOException {
    var bare = new PoiPaymentReportDocxWriter(new ReportClinicProperties("", null, null, null));
    var base = document(false, "0").report();
    var unknown =
        PaymentSummaryReportResponse.Item.builder()
            .batchServiceId(UUID.randomUUID())
            .displayOrder(1)
            .unitPrice(BigDecimal.TEN)
            .examinedCount(0)
            .amount(BigDecimal.ZERO)
            .build();
    var report =
        PaymentSummaryReportResponse.builder()
            .batchId(base.batchId())
            .batchCode(base.batchCode())
            .batchName(base.batchName())
            .batchStatus(base.batchStatus())
            .provisional(false)
            .items(List.of(unknown))
            .totalAmount(BigDecimal.ZERO)
            .generatedAt(GENERATED_AT)
            .build();
    var document =
        new PaymentReportDocument(
            report, "Công ty ABC", null, null, LocalDate.of(2026, 10, 4), null, "Phòng khám", null);

    try (XWPFDocument doc = reopen(bare.write(document))) {
      List<String> text = texts(doc);
      assertThat(text).contains(PoiPaymentReportDocxWriter.UNKNOWN_SERVICE);
      assertThat(text).contains("Ngày 08 tháng 10 năm 2026", "Thời gian: 04/10/2026");
      assertThat(text).noneMatch(t -> t.startsWith("Mã số thuế") || t.startsWith("Địa chỉ"));
      assertThat(text).anyMatch(t -> t.equals("Bằng chữ: Không đồng chẵn"));
    }
  }

  @Test
  void formatsMoneyWithVietnameseSeparators() {
    assertThat(PoiPaymentReportDocxWriter.money(new BigDecimal("1005000.00"))).isEqualTo("1.005.000");
    assertThat(PoiPaymentReportDocxWriter.money(new BigDecimal("1005000.5"))).isEqualTo("1.005.000,50");
    assertThat(PoiPaymentReportDocxWriter.money(BigDecimal.ZERO)).isEqualTo("0");
  }
}
