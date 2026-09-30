package com.ngockhanh.clinic.healthexamination.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

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
            assertThatThrownBy(() -> limitedReader.readHeader(input, SpreadsheetFormat.XLS))
                    .isInstanceOf(IllegalArgumentException.class);
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
