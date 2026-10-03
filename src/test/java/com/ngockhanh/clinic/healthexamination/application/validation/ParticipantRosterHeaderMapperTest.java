package com.ngockhanh.clinic.healthexamination.application.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;

class ParticipantRosterHeaderMapperTest {
    private final ParticipantRosterHeaderMapper mapper = new ParticipantRosterHeaderMapper();

    @Test
    void suggestsMappingForTheSixteenColumnVietnameseRosterTemplate() {
        List<String> headers = List.of(
                "STT", "Họ và tên", "Giới tính", "Ngày/tháng/năm sinh", "Số điện thoại",
                "Số CCCD/Hộ chiếu/Mã định danh", "Cấp ngày", "Nơi cấp", "Dân tộc", "Đối tượng",
                "Nhóm máu (nếu có)", "Nghề nghiệp", "Nơi làm việc", "Chỗ ở hiện tại", "Nguồn chi trả", "Ghi chú");

        Map<ParticipantImportField, Integer> mapping = mapper.suggest(headers);

        assertThat(mapping).containsEntry(ParticipantImportField.FULL_NAME, 1)
                .containsEntry(ParticipantImportField.DATE_OF_BIRTH, 3)
                .containsEntry(ParticipantImportField.IDENTIFICATION_NUMBER, 5)
                .containsEntry(ParticipantImportField.ROSTER_NOTE, 15)
                .containsEntry(ParticipantImportField.OCCUPATION, 11);
    }

    @Test
    void normalizesAccentsCaseAndWhitespaceWhenMatchingHeaders() {
        Map<ParticipantImportField, Integer> mapping = mapper.suggest(List.of(
                "HỌ   VÀ TÊN", "GIỚI TÍNH", "NGÀY SINH", "CCCD"));

        assertThat(mapping).containsEntry(ParticipantImportField.FULL_NAME, 0)
                .containsEntry(ParticipantImportField.SEX, 1)
                .containsEntry(ParticipantImportField.DATE_OF_BIRTH, 2)
                .containsEntry(ParticipantImportField.IDENTIFICATION_NUMBER, 3);
    }
}
