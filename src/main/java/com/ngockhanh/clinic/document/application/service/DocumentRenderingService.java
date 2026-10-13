package com.ngockhanh.clinic.document.application.service;

import com.ngockhanh.clinic.document.application.port.BarcodeImageGenerator;
import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.DocxTemplateEngine;
import com.ngockhanh.clinic.document.application.port.PdfConverter;
import com.ngockhanh.clinic.document.application.port.TemplateCatalog;
import com.ngockhanh.clinic.document.application.rendering.DocumentRenderer;
import com.ngockhanh.clinic.document.application.rendering.RenderSession;
import com.ngockhanh.clinic.document.application.rendering.RenderedDocument;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Renders a document from the template version in effect at the moment of the call. It holds no
 * transaction: the converter call can take seconds and must not hold a database connection.
 */
@Service
public class DocumentRenderingService implements DocumentRenderer {
  private static final Logger log = LoggerFactory.getLogger(DocumentRenderingService.class);

  private final TemplateCatalog catalog;
  private final DocumentStorage storage;
  private final DocxTemplateEngine engine;
  private final PdfConverter converter;
  private final BarcodeImageGenerator barcodes;
  private final Clock clock;

  public DocumentRenderingService(
      TemplateCatalog catalog,
      DocumentStorage storage,
      DocxTemplateEngine engine,
      PdfConverter converter,
      BarcodeImageGenerator barcodes,
      Clock clock) {
    this.catalog = catalog;
    this.storage = storage;
    this.engine = engine;
    this.converter = converter;
    this.barcodes = barcodes;
    this.clock = clock;
  }

  @Override
  public RenderedDocument render(String documentType, Map<String, String> values) {
    if (values == null) throw new IllegalArgumentException("Document type and values are required");
    RenderSession session = open(documentType);
    byte[] pdf = session.render(values);
    log.info(
        "Document rendered: documentType={}, templateVersionId={}, versionNo={}, bytes={}",
        documentType,
        session.templateVersionId(),
        session.templateVersionNo(),
        pdf.length);
    return new RenderedDocument(pdf, session.templateVersionId(), session.templateVersionNo());
  }

  @Override
  public RenderSession open(String documentType) {
    if (documentType == null || documentType.isBlank())
      throw new IllegalArgumentException("Document type and values are required");
    var version =
        catalog
            .findEffective(documentType, clock.instant())
            .orElseThrow(() -> new ResourceNotFoundException("Document template"));
    byte[] template =
        storage.read(version.storageProvider(), version.storageKey(), version.checksum());
    return new RenderSession() {
      @Override
      public UUID templateVersionId() {
        return version.versionId();
      }

      @Override
      public int templateVersionNo() {
        return version.versionNo();
      }

      @Override
      public byte[] render(Map<String, String> values) {
        if (values == null) throw new IllegalArgumentException("Values are required");
        byte[] pdf = converter.toPdf(fill(template, values));
        requireFits(version.pagesPerRecord(), pdf, documentType);
        return pdf;
      }
    };
  }

  /**
   * A stored template that cannot be read is a server fault, not a bad request. Every picture
   * placeholder is a Code 128 barcode of its value, drawn to the size of its frame.
   */
  private byte[] fill(byte[] template, Map<String, String> values) {
    try {
      return engine.fill(
          template,
          values,
          (key, value, slot) -> barcodes.code128Png(value, slot.widthMm(), slot.heightMm()));
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("The stored template cannot be filled", e);
    }
  }

  /**
   * Overflow is a blocking error: a form that spills onto an extra page is wrong on paper, so it is
   * refused instead of printed. A PDF whose pages cannot be counted is let through and logged.
   */
  private static void requireFits(Integer expectedPages, byte[] pdf, String documentType) {
    if (expectedPages == null) return;
    var pages = PdfPageCounter.count(pdf);
    if (pages.isEmpty()) {
      log.warn("Page count unavailable: documentType={}", documentType);
      return;
    }
    if (pages.getAsInt() > expectedPages)
      throw new ConflictException(
          "The form does not fit its "
              + expectedPages
              + " pages; shorten the longest values or ask the clinic to adjust the template");
  }
}
