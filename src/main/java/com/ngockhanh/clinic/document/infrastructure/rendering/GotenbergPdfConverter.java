package com.ngockhanh.clinic.document.infrastructure.rendering;

import com.ngockhanh.clinic.document.application.port.PdfConverter;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Converts Word to PDF through a Gotenberg sidecar (LibreOffice). The sidecar runs without outbound
 * network; this class only posts the file and checks that a PDF came back.
 */
public class GotenbergPdfConverter implements PdfConverter {
  private static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  private final HttpClient client;
  private final URI endpoint;
  private final Duration timeout;

  public GotenbergPdfConverter(HttpClient client, String baseUrl, Duration timeout) {
    if (baseUrl == null || baseUrl.isBlank())
      throw new IllegalArgumentException("Gotenberg URL is required");
    String base = baseUrl.strip();
    this.client = client;
    this.endpoint = URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
        + "/forms/libreoffice/convert");
    this.timeout = timeout;
  }

  @Override
  public byte[] toPdf(byte[] docx) {
    String boundary = "nkc-" + UUID.randomUUID();
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, docx)))
            .build();
    HttpResponse<byte[]> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new DependencyUnavailableException("PDF converter is unreachable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DependencyUnavailableException("PDF conversion was interrupted", e);
    }
    if (response.statusCode() != 200)
      throw new DependencyUnavailableException(
          "PDF converter answered HTTP " + response.statusCode(), null);
    byte[] pdf = response.body();
    if (pdf == null || pdf.length < PDF_MAGIC.length || !startsWithPdfMagic(pdf))
      throw new DependencyUnavailableException("PDF converter returned a non-PDF body", null);
    return pdf;
  }

  private static boolean startsWithPdfMagic(byte[] pdf) {
    for (int i = 0; i < PDF_MAGIC.length; i++) if (pdf[i] != PDF_MAGIC[i]) return false;
    return true;
  }

  private static String field(String boundary, String name, String value) {
    return "\r\n--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value;
  }

  private static byte[] multipart(String boundary, byte[] docx) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(docx.length + 256);
    byte[] head =
        ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"files\"; filename=\"form.docx\"\r\n"
                + "Content-Type: " + DOCX_CONTENT_TYPE + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    // A barcode is a 1-bit picture sized to its frame: neither JPEG compression nor resampling to
    // 300 dpi may touch it, or the bars blur and the scanner misreads.
    byte[] tail =
        (field(boundary, "losslessImageCompression", "true")
                + field(boundary, "reduceImageResolution", "false")
                + "\r\n--" + boundary + "--\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    out.writeBytes(head);
    out.writeBytes(docx);
    out.writeBytes(tail);
    return out.toByteArray();
  }
}
