package com.ngockhanh.clinic.healthcheck.infrastructure.spreadsheet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.junit.jupiter.api.Test;

class FesodParticipantRosterWorkbookReaderTest {
    @Test
    void readsVersionedParticipantWorkbookAndMapsColumnsByHeader() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ExcelWriter writer = FesodSheet.write(output).build()) {
            WriteSheet participants = FesodSheet.writerSheet(0, "Danh sách khám")
                    .head(FesodParticipantTemplateWriter.HEADERS.stream().map(List::of).toList()).build();
            writer.write(List.of(List.of("E-12", "  Nguyễn Văn A  ", "Nam", "01/01/1990", "0912345678",
                    "001234567890", "", "", "", "", "", "", "", "", "", "", "", "", "Phòng A", "Bác sĩ")),
                    participants);

            WriteSheet guide = FesodSheet.writerSheet(1, "Hướng dẫn")
                    .head(List.of(List.of("Thông tin"), List.of("Giá trị"))).build();
            writer.write(List.of(List.of("TEMPLATE_VERSION", "1")), guide);
        }

        var roster = new FesodParticipantRosterWorkbookReader().read(output.toByteArray());

        assertThat(roster.rows()).hasSize(1);
        assertThat(roster.rows().getFirst().rowNumber()).isEqualTo(2);
        assertThat(roster.rows().getFirst().participantCode()).isEqualTo("E-12");
        assertThat(roster.rows().getFirst().snapshot().fullName()).isEqualTo("Nguyễn Văn A");
    }
}
