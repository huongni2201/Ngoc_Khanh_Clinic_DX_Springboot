package com.ngockhanh.clinic.healthcheck.infrastructure.spreadsheet;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.springframework.stereotype.Component;

import com.ngockhanh.clinic.healthcheck.application.port.ParticipantTemplateWriter;

@Component
public final class FesodParticipantTemplateWriter implements ParticipantTemplateWriter {
    static final List<String> HEADERS = List.of(
            "Mã nhân viên", "Họ và tên", "Giới tính", "Ngày sinh", "Số điện thoại",
            "Số định danh/CCCD", "Ngày cấp", "Nơi cấp", "Dân tộc", "Đối tượng",
            "Nguồn chi trả", "Nhóm máu", "Tỉnh/Thành phố", "Phường/Xã", "Địa chỉ chi tiết",
            "Nghề nghiệp", "Nơi làm việc/Trường học", "Lý do khám", "Phòng ban", "Chức vụ");
    private static final List<List<String>> GUIDE_ROWS = List.of(
            List.of("TEMPLATE_VERSION", "1"),
            List.of("Ngày sinh", "Nhập theo định dạng dd/MM/yyyy."),
            List.of("Mã nhân viên", "Bắt buộc và duy nhất trong tệp."),
            List.of("Số định danh/CCCD", "Bắt buộc; không tạo Patient khi import."));

    @Override
    public byte[] generate() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ExcelWriter writer = FesodSheet.write(output).build()) {
            WriteSheet participantSheet = FesodSheet.writerSheet(0, "Danh sách khám")
                    .head(HEADERS.stream().map(List::of).toList()).build();
            writer.write(List.of(), participantSheet);

            WriteSheet guideSheet = FesodSheet.writerSheet(1, "Hướng dẫn")
                    .head(List.of(List.of("Thông tin"), List.of("Giá trị"))).build();
            writer.write(GUIDE_ROWS, guideSheet);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to generate participant import template", failure);
        }
        return output.toByteArray();
    }
}
