package com.ngockhanh.clinic.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.BarcodeImageGenerator;
import com.ngockhanh.clinic.document.application.port.DocxTemplateEngine;
import com.ngockhanh.clinic.document.application.port.ImageSlot;
import com.ngockhanh.clinic.document.application.port.EffectiveTemplateVersion;
import com.ngockhanh.clinic.document.application.port.PdfConverter;
import com.ngockhanh.clinic.document.application.port.TemplateCatalog;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentRenderingServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-12T02:00:00Z");

  private final TemplateCatalog catalog = mock(TemplateCatalog.class);
  private final DocumentStorage storage = mock(DocumentStorage.class);
  private final DocxTemplateEngine engine = mock(DocxTemplateEngine.class);
  private final PdfConverter converter = mock(PdfConverter.class);
  private final BarcodeImageGenerator barcodes = mock(BarcodeImageGenerator.class);
  private final DocumentRenderingService service =
      new DocumentRenderingService(
          catalog, storage, engine, converter, barcodes, Clock.fixed(NOW, ZoneOffset.UTC));
  private final UUID versionId = UUID.randomUUID();
  private final byte[] checksum = new byte[] {9};

  @Test
  void rendersTheVersionInEffectNow() {
    var values = Map.of("participant.code", "C1");
    when(catalog.findEffective("TYPE", NOW))
        .thenReturn(
            Optional.of(
                new EffectiveTemplateVersion(versionId, 3, 1, "CLASSPATH", "k.docx", checksum, 2)));
    when(storage.read("CLASSPATH", "k.docx", checksum)).thenReturn(new byte[] {1});
    when(engine.fill(eq(new byte[] {1}), eq(values), any())).thenReturn(new byte[] {2});
    when(converter.toPdf(new byte[] {2})).thenReturn(new byte[] {3});

    var rendered = service.render("TYPE", values);

    assertThat(rendered.pdf()).containsExactly(3);
    assertThat(rendered.templateVersionId()).isEqualTo(versionId);
    assertThat(rendered.templateVersionNo()).isEqualTo(3);
  }

  @Test
  void noEffectiveVersionIsNotFoundAndNothingIsConverted() {
    when(catalog.findEffective(eq("TYPE"), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.render("TYPE", Map.of()))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(storage, engine, converter);
  }

  @Test
  void aFormThatSpillsOntoAnExtraPageIsRefused() {
    effective(2);
    when(converter.toPdf(any())).thenReturn(pdfWithPages(3));

    assertThatThrownBy(() -> service.render("TYPE", Map.of()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("2 pages");
  }

  @Test
  void aFormThatFitsItsPagesIsReturned() {
    effective(2);
    byte[] pdf = pdfWithPages(2);
    when(converter.toPdf(any())).thenReturn(pdf);

    assertThat(service.render("TYPE", Map.of()).pdf()).isEqualTo(pdf);
  }

  @Test
  void aTemplateWithVariablePageCountIsNotChecked() {
    effective(null);
    byte[] pdf = pdfWithPages(9);
    when(converter.toPdf(any())).thenReturn(pdf);

    assertThat(service.render("TYPE", Map.of()).pdf()).isEqualTo(pdf);
  }

  @Test
  void aStoredTemplateThatCannotBeReadIsAServerFaultNotABadRequest() {
    effective(2);
    when(engine.fill(any(), any(), any())).thenThrow(new IllegalArgumentException("corrupt"));

    assertThatThrownBy(() -> service.render("TYPE", Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(converter);
  }

  private void effective(Integer pagesPerRecord) {
    when(catalog.findEffective("TYPE", NOW))
        .thenReturn(
            Optional.of(
                new EffectiveTemplateVersion(
                    versionId, 1, 1, "CLASSPATH", "k.docx", checksum, pagesPerRecord)));
    when(storage.read(any(), any(), any())).thenReturn(new byte[] {1});
    when(engine.fill(any(), any(), any())).thenReturn(new byte[] {2});
  }

  private static byte[] pdfWithPages(int pages) {
    StringBuilder pdf = new StringBuilder("%PDF-1.7\n");
    for (int i = 0; i < pages; i++) pdf.append(i + 1).append(" 0 obj\n<</Type/Page>>\nendobj\n");
    pdf.append("99 0 obj\n<</Type/Pages/Count ").append(pages).append(">>\nendobj\n%%EOF");
    return pdf.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
  }

  @Test
  void aSessionReadsTheTemplateOnceAndRendersManyRecordsWithTheSameVersion() {
    effective(2);
    when(converter.toPdf(any())).thenReturn(new byte[] {3});

    var session = service.open("TYPE");
    session.render(Map.of("participant.code", "C1"));
    session.render(Map.of("participant.code", "C2"));

    assertThat(session.templateVersionId()).isEqualTo(versionId);
    assertThat(session.templateVersionNo()).isEqualTo(1);
    verify(catalog, times(1)).findEffective(any(), any());
    verify(storage, times(1)).read(any(), any(), any());
    verify(engine).fill(eq(new byte[] {1}), eq(Map.of("participant.code", "C1")), any());
    verify(engine).fill(eq(new byte[] {1}), eq(Map.of("participant.code", "C2")), any());
    verify(converter, times(2)).toPdf(new byte[] {2});
  }

  @Test
  void aSessionRefusesOneFormThatDoesNotFitAndGoesOnWithTheNext() {
    effective(2);
    when(converter.toPdf(any())).thenReturn(pdfWithPages(3), pdfWithPages(2));

    var session = service.open("TYPE");

    assertThatThrownBy(() -> session.render(Map.of())).isInstanceOf(ConflictException.class);
    assertThat(session.render(Map.of())).isNotEmpty();
  }

  @Test
  void openingWithoutAnEffectiveVersionIsNotFoundAndNothingIsRead() {
    when(catalog.findEffective(eq("TYPE"), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.open("TYPE")).isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(storage, engine, converter);
  }

  @Test
  void everyPicturePlaceholderBecomesACode128OfItsValueAtTheSizeOfItsFrame() {
    effective(null);
    when(converter.toPdf(any())).thenReturn(new byte[] {3});
    var png = new byte[] {7};
    when(barcodes.code128Png("KSK-1", 48.9, 9.0)).thenReturn(png);
    var source = org.mockito.ArgumentCaptor.forClass(DocxTemplateEngine.ImageSource.class);

    service.render("TYPE", Map.of("participant.barcode", "KSK-1"));

    verify(engine).fill(eq(new byte[] {1}), eq(Map.of("participant.barcode", "KSK-1")), source.capture());
    assertThat(
            source
                .getValue()
                .image("participant.barcode", "KSK-1", new ImageSlot("participant.barcode", 48.9, 9.0)))
        .isSameAs(png);
  }
}
