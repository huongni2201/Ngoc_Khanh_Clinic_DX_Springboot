package com.ngockhanh.clinic.healthcheck.infrastructure.spreadsheet;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.fesod.sheet.FesodSheet;
import org.springframework.stereotype.Component;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParsedParticipantRoster;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportWorkbookException;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.RawAdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.RawParticipantImportRow;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantRosterWorkbookReader;

@Component
public final class FesodParticipantRosterWorkbookReader implements ParticipantRosterWorkbookReader {
    private static final int TEMPLATE_VERSION = 1;
    private static final int MAX_ROWS = 20_000;
    private static final String PARTICIPANT_SHEET = "Danh sách khám";
    private static final String GUIDE_SHEET = "Hướng dẫn";

    @Override
    public ParsedParticipantRoster read(byte[] content) {
        if (content == null || content.length == 0) throw new ParticipantImportWorkbookException("EMPTY_FILE");
        try {
            SheetData guide = readSheet(content, GUIDE_SHEET);
            if (guide.rows().values().stream().noneMatch(row -> "TEMPLATE_VERSION".equalsIgnoreCase(cell(row, 0))
                    && Integer.toString(TEMPLATE_VERSION).equals(cell(row, 1)))) {
                throw new ParticipantImportWorkbookException("UNSUPPORTED_TEMPLATE_VERSION");
            }

            SheetData sheet = readSheet(content, PARTICIPANT_SHEET);
            Map<String, Integer> columns = mapHeaders(sheet.headers());
            List<RawParticipantImportRow> rows = new ArrayList<>();
            for (Map.Entry<Integer, Map<Integer, String>> entry : sheet.rows().entrySet()) {
                Map<Integer, String> row = entry.getValue();
                if (row.values().stream().allMatch(FesodParticipantRosterWorkbookReader::blank)) continue;
                int rowNumber = entry.getKey();
                rows.add(new RawParticipantImportRow(rowNumber,
                        value(row, columns, "participantCode"), value(row, columns, "departmentName"),
                        value(row, columns, "jobTitle"), value(row, columns, "occupation"),
                        new RawAdministrativeSnapshot(value(row, columns, "fullName"),
                                value(row, columns, "dateOfBirth"), value(row, columns, "sex"),
                                value(row, columns, "identificationNumber"), value(row, columns, "issueDate"),
                                value(row, columns, "issuePlace"), value(row, columns, "ethnicity"),
                                value(row, columns, "subjectType"), value(row, columns, "payerSource"),
                                value(row, columns, "bloodGroup"), value(row, columns, "phone"),
                                value(row, columns, "province"), value(row, columns, "ward"),
                                value(row, columns, "addressDetail"), value(row, columns, "occupation"),
                                value(row, columns, "workplace"), value(row, columns, "healthReason"))));
                if (rows.size() > MAX_ROWS) throw new ParticipantImportWorkbookException("MAX_ROW_COUNT_EXCEEDED");
            }
            if (rows.isEmpty()) throw new ParticipantImportWorkbookException("EMPTY_WORKSHEET");
            return new ParsedParticipantRoster(rows);
        } catch (ParticipantImportWorkbookException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ParticipantImportWorkbookException("INVALID_WORKBOOK");
        }
    }

    private static SheetData readSheet(byte[] content, String sheetName) {
        List<Map<Integer, String>> parsed;
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            parsed = FesodSheet.read(input).sheet(sheetName).headRowNumber(0).doReadSync();
        } catch (Exception failure) {
            throw new ParticipantImportWorkbookException("REQUIRED_SHEET_MISSING", failure);
        }
        if (parsed.isEmpty()) throw new ParticipantImportWorkbookException("MISSING_HEADER_ROW");
        Map<Integer, String> headers = parsed.getFirst();
        Map<Integer, Map<Integer, String>> rows = new java.util.TreeMap<>();
        for (int index = 1; index < parsed.size(); index++) rows.put(index + 1, parsed.get(index));
        return new SheetData(headers, rows);
    }

    private static Map<String, Integer> mapHeaders(Map<Integer, String> headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (Map.Entry<Integer, String> header : headers.entrySet()) {
            String key = headerKey(header.getValue());
            if (!key.isEmpty() && columns.putIfAbsent(key, header.getKey()) != null) {
                throw new ParticipantImportWorkbookException("DUPLICATED_HEADER");
            }
        }
        Map<String, Integer> mapped = new HashMap<>();
        columns.forEach((header, index) -> {
            String field = switch (header) {
                case "manhanvien", "maparticipant", "mathanhvien", "manguoithamgia" -> "participantCode";
                case "hovaten", "hoten" -> "fullName";
                case "gioitinh" -> "sex";
                case "ngaysinh" -> "dateOfBirth";
                case "sodienthoai", "dienthoai" -> "phone";
                case "sodinhdanhcccd", "cccd", "sodinhdanh" -> "identificationNumber";
                case "ngaycap", "ngaycapcccd" -> "issueDate";
                case "noicap", "noicapcccd" -> "issuePlace";
                case "dantoc" -> "ethnicity";
                case "doituong" -> "subjectType";
                case "nguonchitra" -> "payerSource";
                case "nhommau" -> "bloodGroup";
                case "tinhtp", "tinhthanhpho" -> "province";
                case "phuongxa" -> "ward";
                case "diachichitiet" -> "addressDetail";
                case "nghenghiep" -> "occupation";
                case "noilamviectruonghoc" -> "workplace";
                case "lydokham" -> "healthReason";
                case "phongban" -> "departmentName";
                case "chucvu" -> "jobTitle";
                default -> null;
            };
            if (field != null && mapped.putIfAbsent(field, index) != null) {
                throw new ParticipantImportWorkbookException("DUPLICATED_HEADER");
            }
        });
        for (String required : List.of("participantCode", "fullName", "sex", "dateOfBirth", "identificationNumber")) {
            if (!mapped.containsKey(required)) throw new ParticipantImportWorkbookException("MISSING_REQUIRED_COLUMN");
        }
        return mapped;
    }

    private static String headerKey(String header) {
        if (blank(header)) return "";
        return Normalizer.normalize(header, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replace('đ', 'd').replaceAll("[^a-z0-9]", "");
    }

    private static String value(Map<Integer, String> row, Map<String, Integer> columns, String field) {
        Integer index = columns.get(field);
        return index == null ? null : row.get(index);
    }

    private static String cell(Map<Integer, String> row, int index) {
        return row.get(index);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record SheetData(Map<Integer, String> headers, Map<Integer, Map<Integer, String>> rows) {
    }
}
