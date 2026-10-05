package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.application.command.UploadParticipantImportCommand;
import com.ngockhanh.clinic.healthexamination.application.port.out.*;
import com.ngockhanh.clinic.healthexamination.application.response.*;
import com.ngockhanh.clinic.healthexamination.application.validation.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class UploadParticipantImportUseCaseTest {
  final HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
  final ImportFileStorage storage = mock(ImportFileStorage.class);
  final ParticipantSpreadsheetReader reader = mock(ParticipantSpreadsheetReader.class);
  final StoreValidatedParticipantImportUseCase store =
      mock(StoreValidatedParticipantImportUseCase.class);
  final UploadParticipantImportUseCase usecase =
      new UploadParticipantImportUseCase(
          batches,
          storage,
          reader,
          new ParticipantRosterHeaderMapper(),
          store,
          new ParticipantRosterRowValidator());

  private UploadParticipantImportCommand command() {
    return new UploadParticipantImportCommand(
        id(2).value(),
        id(1).value(),
        id(6).value(),
        "roster.xlsx",
        "application/octet-stream",
        4,
        new ByteArrayInputStream(new byte[] {'P', 'K', 3, 4}),
        List.of(id(3).value()));
  }

  private void setup() throws Exception {
    when(batches.findByIdAndOrganizationId(id(1), id(2))).thenReturn(Optional.of(batch()));
    when(storage.store(any(), any(), anyLong()))
        .thenReturn(new ImportFileStorage.StoredFile("temp", 4, "digest"));
    when(storage.open("temp"))
        .thenAnswer(i -> new ByteArrayInputStream(new byte[] {'P', 'K', 3, 4}));
  }

  @Test
  void rejectsInvalidHeaderWithoutStagingAndDeletesTemporarySource() throws Exception {
    setup();
    when(reader.readHeader(any(), any()))
        .thenReturn(new ParticipantSpreadsheetReader.SpreadsheetHeader(2, List.of("Bad header")));
    assertThatThrownBy(() -> usecase.execute(command()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(store);
    verify(storage, atLeastOnce()).delete("temp");
  }

  @Test
  void rejectsWorkbookBytesThatDoNotMatchTheExcelExtensionBeforeStorage() throws Exception {
    setup();
    var invalid = command();
    var invalidFile =
        new UploadParticipantImportCommand(
            invalid.organizationId(),
            invalid.batchId(),
            invalid.actorUserId(),
            invalid.fileName(),
            invalid.contentType(),
            invalid.sizeBytes(),
            new ByteArrayInputStream(
                "not an Excel workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            invalid.selectedBatchDayIds());

    assertThatThrownBy(() -> usecase.execute(invalidFile))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");

    verifyNoInteractions(storage, reader, store);
  }

  @Test
  void validatesAllRowsBeforeCallingDurableStagingAndDeletesTemporarySource() throws Exception {
    setup();
    when(reader.readHeader(any(), any()))
        .thenReturn(
            new ParticipantSpreadsheetReader.SpreadsheetHeader(
                2, ParticipantRosterHeaderMapper.HEADERS));
    doAnswer(
            i -> {
              java.util.function.Consumer<ParticipantSpreadsheetReader.SpreadsheetRow> consumer =
                  i.getArgument(2);
              consumer.accept(
                  new ParticipantSpreadsheetReader.SpreadsheetRow(
                      3,
                      Map.of(
                          2,
                          "Synthetic Person",
                          3,
                          "01/01/1990",
                          4,
                          "Nam",
                          5,
                          "000000000001",
                          8,
                          "Department",
                          9,
                          "Position")));
              return null;
            })
        .when(reader)
        .readRows(any(), any(), any());
    when(store.execute(any(), any(), any(), anyList(), anyList()))
        .thenReturn(
            new ParticipantImportUploadResponse(
                id(5).value(), "VALIDATED", 0, 1, List.of(id(3).value()), List.of()));
    assertThat(usecase.execute(command()).importId()).isEqualTo(id(5).value());
    verify(store)
        .execute(
            eq(id(2).value()),
            eq(id(1).value()),
            eq(id(6).value()),
            eq(List.of(id(3).value())),
            argThat(rows -> rows.size() == 1 && rows.getFirst().isValid()));
    verify(storage).delete("temp");
  }
}
