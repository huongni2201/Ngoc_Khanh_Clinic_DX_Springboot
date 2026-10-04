package com.ngockhanh.clinic.healthexamination.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader.SpreadsheetFormat;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class FesodParticipantTemplateWriterTest {
  @Test
  void generatesAnEmptyTemplateWithTheRosterHeadersOnRowTwo() {
    byte[] template = new FesodParticipantTemplateWriter().generate();
    ParticipantSpreadsheetReader reader = new FesodParticipantSpreadsheetReader();

    ParticipantSpreadsheetReader.SpreadsheetHeader header =
        reader.readHeader(new ByteArrayInputStream(template), SpreadsheetFormat.XLSX);

    assertThat(template).startsWith((byte) 'P', (byte) 'K');
    assertThat(header.rowNumber()).isEqualTo(2);
    assertThat(header.values())
        .containsExactly(
            "STT",
            "Mã nhân viên",
            "Họ và tên",
            "Ngày sinh",
            "Giới tính",
            "CCCD",
            "Điện thoại",
            "Email",
            "Phòng ban",
            "Chức danh");
  }
}
