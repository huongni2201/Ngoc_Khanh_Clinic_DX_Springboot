package com.ngockhanh.clinic.document.infrastructure.rendering;

import com.ngockhanh.clinic.document.application.port.PdfMerger;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Merges PDF files through the Gotenberg sidecar ({@code /forms/pdfengines/merge}). Gotenberg joins
 * the files in alphabetical order of their names, so each is sent as a zero-padded number.
 */
public class GotenbergPdfMerger implements PdfMerger {
  private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  private final HttpClient client;
  private final URI endpoint;
  private final Duration timeout;

  public GotenbergPdfMerger(HttpClient client, String baseUrl, Duration timeout) {
    if (baseUrl == null || baseUrl.isBlank())
      throw new IllegalArgumentException("Gotenberg URL is required");
    String base = baseUrl.strip();
    this.client = client;
    this.endpoint =
        URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
            + "/forms/pdfengines/merge");
    this.timeout = timeout;
  }

  @Override
  public byte[] merge(List<byte[]> pdfs) {
    if (pdfs == null || pdfs.isEmpty()) throw new IllegalArgumentException("Nothing to merge");
    String boundary = "nkc-" + UUID.randomUUID();
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, pdfs)))
            .build();
    HttpResponse<byte[]> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new DependencyUnavailableException("PDF merger is unreachable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DependencyUnavailableException("PDF merge was interrupted", e);
    }
    if (response.statusCode() != 200)
      throw new DependencyUnavailableException(
          "PDF merger answered HTTP " + response.statusCode(), null);
    byte[] merged = response.body();
    if (merged == null || merged.length < PDF_MAGIC.length || !startsWithPdfMagic(merged))
      throw new DependencyUnavailableException("PDF merger returned a non-PDF body", null);
    return merged;
  }

  private static boolean startsWithPdfMagic(byte[] pdf) {
    for (int i = 0; i < PDF_MAGIC.length; i++) if (pdf[i] != PDF_MAGIC[i]) return false;
    return true;
  }

  private static byte[] multipart(String boundary, List<byte[]> pdfs) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int width = Math.max(4, String.valueOf(pdfs.size()).length());
    for (int i = 0; i < pdfs.size(); i++) {
      String name = String.format("%0" + width + "d.pdf", i + 1);
      out.writeBytes(
          ("--" + boundary + "\r\n"
                  + "Content-Disposition: form-data; name=\"files\"; filename=\"" + name + "\"\r\n"
                  + "Content-Type: application/pdf\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      out.writeBytes(pdfs.get(i));
      out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
    out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    return out.toByteArray();
  }
}
