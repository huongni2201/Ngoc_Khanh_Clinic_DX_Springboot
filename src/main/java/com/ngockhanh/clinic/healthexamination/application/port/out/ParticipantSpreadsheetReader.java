package com.ngockhanh.clinic.healthexamination.application.port.out;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ParticipantSpreadsheetReader {
    SpreadsheetHeader readHeader(InputStream input, SpreadsheetFormat format);

    void readRows(InputStream input, SpreadsheetFormat format, Consumer<SpreadsheetRow> consumer);

    enum SpreadsheetFormat {
        XLS,
        XLSX
    }

    record SpreadsheetHeader(int rowNumber, List<String> values) {
        public SpreadsheetHeader {
            if (rowNumber < 1 || values == null) throw new IllegalArgumentException("Invalid spreadsheet header");
            values = List.copyOf(values);
        }
    }

    record SpreadsheetRow(int rowNumber, Map<Integer, String> cells) {
        public SpreadsheetRow {
            if (rowNumber < 1 || cells == null) throw new IllegalArgumentException("Invalid spreadsheet row");
            cells = Map.copyOf(cells);
        }
    }
}
