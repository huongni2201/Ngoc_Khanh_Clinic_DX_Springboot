package com.ngockhanh.clinic.healthexamination.infrastructure.spreadsheet;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.read.builder.ExcelReaderBuilder;
import org.apache.fesod.sheet.support.ExcelTypeEnum;
import org.springframework.stereotype.Component;

@Component
public final class FesodParticipantSpreadsheetReader implements ParticipantSpreadsheetReader {
    private final int maxRows;

    public FesodParticipantSpreadsheetReader() {
        this(10_000);
    }

    @Autowired
    public FesodParticipantSpreadsheetReader(
            @Value("${clinic.health-examination.employee-import.max-rows:10000}") int maxRows) {
        if (maxRows < 1) throw new IllegalArgumentException("Maximum import row count must be positive");
        this.maxRows = maxRows;
    }

    @Override
    public SpreadsheetHeader readHeader(InputStream input, SpreadsheetFormat format) {
        AtomicReference<SpreadsheetHeader> header = new AtomicReference<>();
        read(input, format, (rowNumber, cells) -> {
            if (rowNumber == 2) header.set(new SpreadsheetHeader(rowNumber, values(cells)));
        }, 2);
        SpreadsheetHeader result = header.get();
        if (result == null) throw new IllegalArgumentException("Roster template header is missing");
        return result;
    }

    @Override
    public void readRows(InputStream input, SpreadsheetFormat format, Consumer<SpreadsheetRow> consumer) {
        if (consumer == null) throw new IllegalArgumentException("Spreadsheet row consumer is required");
        read(input, format, (rowNumber, cells) -> {
            if (rowNumber >= 3) consumer.accept(new SpreadsheetRow(rowNumber, cells));
        }, 0);
    }

    private void read(InputStream input, SpreadsheetFormat format, RowHandler handler, int rowLimit) {
        if (input == null || format == null || handler == null) {
            throw new IllegalArgumentException("Spreadsheet input is required");
        }
        int[] dataRows = {0};
        boolean[] limitExceeded = {false};
        ExcelReaderBuilder workbook = FesodSheet.read(input)
                .excelType(format == SpreadsheetFormat.XLS ? ExcelTypeEnum.XLS : ExcelTypeEnum.XLSX)
                .autoCloseStream(false);
        if (rowLimit > 0) workbook.numRows(rowLimit);
        try {
            workbook.sheet(0)
                .headRowNumber(1)
                .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                    @Override
                    public void invoke(Map<Integer, String> cells, AnalysisContext context) {
                        int rowNumber = context.readRowHolder().getRowIndex() + 1;
                        if (rowNumber >= 3 && hasContent(cells) && ++dataRows[0] > maxRows) {
                            limitExceeded[0] = true;
                            throw new IllegalArgumentException("Spreadsheet exceeds configured row limit");
                        }
                        handler.accept(rowNumber, cells == null ? Map.of() : immutableCells(cells));
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        // No workbook-wide state is needed after the last row.
                    }
                })
                .doRead();
        } catch (RuntimeException failure) {
            if (limitExceeded[0]) throw new IllegalArgumentException("Spreadsheet exceeds configured row limit");
            throw failure;
        }
    }

    private static List<String> values(Map<Integer, String> cells) {
        int lastColumn = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        List<String> values = new ArrayList<>(lastColumn + 1);
        for (int index = 0; index <= lastColumn; index++) values.add(cells.getOrDefault(index, ""));
        return values;
    }

    private static Map<Integer, String> immutableCells(Map<Integer, String> cells) {
        return Collections.unmodifiableMap(new HashMap<>(cells));
    }

    private static boolean hasContent(Map<Integer, String> cells) {
        return cells != null && cells.values().stream().anyMatch(value -> value != null && !value.isBlank());
    }

    @FunctionalInterface
    private interface RowHandler {
        void accept(int rowNumber, Map<Integer, String> cells);
    }
}
