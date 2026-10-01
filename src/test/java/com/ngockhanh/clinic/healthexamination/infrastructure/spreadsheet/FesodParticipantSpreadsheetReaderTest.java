package com.ngockhanh.clinic.healthexamination.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader.SpreadsheetFormat;

class FesodParticipantSpreadsheetReaderTest {
    private static final List<String> HEADERS = List.of(
            "STT", "Họ và tên", "Giới tính", "Ngày/tháng/năm sinh", "Số điện thoại",
            "Số CCCD/Hộ chiếu/Mã định danh", "Cấp ngày", "Nơi cấp", "Dân tộc", "Đối tượng",
            "Nhóm máu (nếu có)", "Nghề nghiệp", "Nơi làm việc", "Chỗ ở hiện tại", "Nguồn chi trả", "Ghi chú");

    private final ParticipantSpreadsheetReader reader = new FesodParticipantSpreadsheetReader();

    @Test
    void readsLegacyXlsHeaderAndKeepsPhysicalRowNumbers() throws Exception {
        assertFixture("/import/roster-fixture.xls", SpreadsheetFormat.XLS);
    }

    @Test
    void readsXlsxHeaderAndKeepsTextDates() throws Exception {
        assertFixture("/import/roster-fixture.xlsx", SpreadsheetFormat.XLSX);
    }

    @Test
    void rejectsWorkbookAboveCandidateRowLimit() throws Exception {
        ParticipantSpreadsheetReader limitedReader = new FesodParticipantSpreadsheetReader(1);
        try (InputStream input = getClass().getResourceAsStream("/import/roster-fixture.xls")) {
            assertThatThrownBy(() -> limitedReader.readRows(input, SpreadsheetFormat.XLS, ignored -> { }))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void readsSparseRowsWithoutDroppingLaterDataAndLimitsOnlyNonemptyRows() throws Exception {
        for (SpreadsheetFormat format : SpreadsheetFormat.values()) {
            byte[] workbook = sparseWorkbook(format);
            List<Integer> rowNumbers = new ArrayList<>();

            new FesodParticipantSpreadsheetReader(2).readRows(
                    new java.io.ByteArrayInputStream(workbook), format,
                    row -> rowNumbers.add(row.rowNumber()));

            assertThat(rowNumbers).containsExactly(3, 8);
            assertThatThrownBy(() -> new FesodParticipantSpreadsheetReader(1).readRows(
                    new java.io.ByteArrayInputStream(workbook), format, ignored -> { }))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void readsOnlyTheHeaderEvenWhenTheWorkbookExceedsTheDataRowLimit() throws Exception {
        var limitedReader = new FesodParticipantSpreadsheetReader(1);
        try (InputStream input = getClass().getResourceAsStream("/import/roster-fixture.xls")) {
            assertThat(limitedReader.readHeader(input, SpreadsheetFormat.XLS).rowNumber()).isEqualTo(2);
        }
    }

    private static byte[] sparseWorkbook(SpreadsheetFormat format) throws Exception {
        Workbook workbook = format == SpreadsheetFormat.XLS ? new HSSFWorkbook() : new XSSFWorkbook();
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("roster");
            sheet.createRow(0).createCell(0).setCellValue("Roster");
            sheet.createRow(1).createCell(0).setCellValue("Full name");
            sheet.createRow(2).createCell(0).setCellValue("First person");
            sheet.createRow(7).createCell(0).setCellValue("Last person");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void assertFixture(String resource, SpreadsheetFormat format) throws Exception {
        ParticipantSpreadsheetReader.SpreadsheetHeader header;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            header = reader.readHeader(input, format);
        }

        List<ParticipantSpreadsheetReader.SpreadsheetRow> rows = new ArrayList<>();
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            reader.readRows(input, format, rows::add);
        }

        assertThat(header.rowNumber()).isEqualTo(2);
        assertThat(header.values()).containsExactlyElementsOf(HEADERS);
        assertThat(rows).extracting(ParticipantSpreadsheetReader.SpreadsheetRow::rowNumber)
                .containsExactly(3, 4);
        assertThat(rows.getFirst().cells()).containsEntry(3, "01/01/1990")
                .containsEntry(5, "000000000001");
        assertThat(rows.get(1).cells()).containsEntry(1, "MISSING DATA")
                .doesNotContainKey(3).doesNotContainKey(5);
    }
}
