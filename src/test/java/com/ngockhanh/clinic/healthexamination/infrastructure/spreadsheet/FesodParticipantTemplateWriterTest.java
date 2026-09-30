package com.ngockhanh.clinic.healthexamination.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader.SpreadsheetFormat;

class FesodParticipantTemplateWriterTest {
    @Test
    void generatesAnEmptyTemplateWithTheRosterHeadersOnRowTwo() {
        byte[] template = new FesodParticipantTemplateWriter().generate();
        ParticipantSpreadsheetReader reader = new FesodParticipantSpreadsheetReader();

        ParticipantSpreadsheetReader.SpreadsheetHeader header = reader.readHeader(
                new ByteArrayInputStream(template), SpreadsheetFormat.XLSX);

        assertThat(template).startsWith((byte) 'P', (byte) 'K');
        assertThat(header.rowNumber()).isEqualTo(2);
        assertThat(header.values()).containsExactly(
                "STT", "Họ và tên", "Giới tính", "Ngày/tháng/năm sinh", "Số điện thoại",
                "Số CCCD/Hộ chiếu/Mã định danh", "Cấp ngày", "Nơi cấp", "Dân tộc", "Đối tượng",
                "Nhóm máu (nếu có)", "Nghề nghiệp", "Nơi làm việc", "Chỗ ở hiện tại", "Nguồn chi trả", "Ghi chú");
    }
}
