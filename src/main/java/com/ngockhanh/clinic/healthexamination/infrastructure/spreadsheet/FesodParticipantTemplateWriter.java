package com.ngockhanh.clinic.healthexamination.infrastructure.spreadsheet;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.metadata.data.DataFormatData;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.write.handler.CellWriteHandler;
import org.apache.fesod.sheet.write.handler.context.CellWriteHandlerContext;
import org.springframework.stereotype.Component;

@Component
public final class FesodParticipantTemplateWriter
    implements com.ngockhanh.clinic.healthexamination.application.port.out
        .ParticipantImportTemplateWriter {
  private static final String TITLE = "DANH SÁCH NGƯỜI KHÁM";
  private static final List<String> HEADERS =
      com.ngockhanh.clinic.healthexamination.application.validation.ParticipantRosterHeaderMapper
          .HEADERS;

  @Override
  public byte[] generate() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    List<List<String>> headers = HEADERS.stream().map(header -> List.of(TITLE, header)).toList();
    List<String> blankRow = Collections.nCopies(HEADERS.size(), "");
    FesodSheet.write(output)
        .head(headers)
        .automaticMergeHead(true)
        .registerWriteHandler(new TextColumnWriteHandler())
        .sheet("Danh sách khám")
        .doWrite(List.of(blankRow));
    return output.toByteArray();
  }

  private static final class TextColumnWriteHandler implements CellWriteHandler {
    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
      if (Boolean.TRUE.equals(context.getHead())
          || (context.getColumnIndex() != 5 && context.getColumnIndex() != 6)) return;

      WriteCellData<?> cell = context.getFirstCellData();
      if (cell == null) return;
      DataFormatData format = new DataFormatData();
      format.setFormat("@");
      cell.getOrCreateStyle().setDataFormatData(format);
    }
  }
}
