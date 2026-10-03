package com.ngockhanh.clinic.healthexamination.application.validation;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import org.springframework.stereotype.Component;

@Component
public final class ParticipantRosterHeaderMapper {
    private static final Map<String, ParticipantImportField> HEADER_FIELDS = Map.ofEntries(
            Map.entry("HO VA TEN", ParticipantImportField.FULL_NAME),
            Map.entry("HO TEN", ParticipantImportField.FULL_NAME),
            Map.entry("GIOI TINH", ParticipantImportField.SEX),
            Map.entry("NGAY THANG NAM SINH", ParticipantImportField.DATE_OF_BIRTH),
            Map.entry("NGAY SINH", ParticipantImportField.DATE_OF_BIRTH),
            Map.entry("SO DIEN THOAI", ParticipantImportField.PHONE),
            Map.entry("SO CCCD HO CHIEU MA DINH DANH", ParticipantImportField.IDENTIFICATION_NUMBER),
            Map.entry("SO CCCD", ParticipantImportField.IDENTIFICATION_NUMBER),
            Map.entry("CCCD", ParticipantImportField.IDENTIFICATION_NUMBER),
            Map.entry("CAP NGAY", ParticipantImportField.IDENTIFICATION_NUMBER_ISSUE_DATE),
            Map.entry("NOI CAP", ParticipantImportField.IDENTIFICATION_NUMBER_ISSUE_PLACE),
            Map.entry("DAN TOC", ParticipantImportField.ETHNICITY),
            Map.entry("DOI TUONG", ParticipantImportField.SUBJECT_TYPE),
            Map.entry("NHOM MAU NEU CO", ParticipantImportField.BLOOD_GROUP),
            Map.entry("NHOM MAU", ParticipantImportField.BLOOD_GROUP),
            Map.entry("NGHE NGHIEP", ParticipantImportField.OCCUPATION),
            Map.entry("NOI LAM VIEC", ParticipantImportField.WORKPLACE_OR_SCHOOL),
            Map.entry("CHO O HIEN TAI", ParticipantImportField.ADDRESS_DETAIL),
            Map.entry("NGUON CHI TRA", ParticipantImportField.PAYER_SOURCE),
            Map.entry("GHI CHU", ParticipantImportField.ROSTER_NOTE));

    public Map<ParticipantImportField, Integer> suggest(List<String> headers) {
        if (headers == null) throw new IllegalArgumentException("Spreadsheet headers are required");

        EnumMap<ParticipantImportField, Integer> mapping = new EnumMap<>(ParticipantImportField.class);
        for (int index = 0; index < headers.size(); index++) {
            ParticipantImportField field = HEADER_FIELDS.get(normalize(headers.get(index)));
            if (field == null) continue;
            if (mapping.putIfAbsent(field, index) != null) {
                throw new IllegalArgumentException("Spreadsheet maps more than one column to the same field");
            }
        }
        return Map.copyOf(mapping);
    }

    public static String normalize(String header) {
        if (header == null) return "";
        String decomposed = Normalizer.normalize(header.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }
}
