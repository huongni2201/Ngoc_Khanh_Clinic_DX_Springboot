package com.ngockhanh.clinic.document.infrastructure.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GotenbergPdfMergerTest {
  private HttpServer server;
  private final AtomicReference<String> contentType = new AtomicReference<>();
  private final AtomicReference<byte[]> requestBody = new AtomicReference<>();
  private volatile int status = 200;
  private volatile byte[] answer = "%PDF-1.7 merged".getBytes(StandardCharsets.US_ASCII);
  private final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(2))
          .build();

  @BeforeEach
  void startFakeGotenberg() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/forms/pdfengines/merge",
        exchange -> {
          contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          requestBody.set(exchange.getRequestBody().readAllBytes());
          exchange.sendResponseHeaders(status, answer.length);
          exchange.getResponseBody().write(answer);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopFakeGotenberg() {
    server.stop(0);
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private GotenbergPdfMerger merger(String url) {
    return new GotenbergPdfMerger(client, url, Duration.ofSeconds(5));
  }

  private static byte[] pdf(String text) {
    return ("%PDF-1.7 " + text).getBytes(StandardCharsets.US_ASCII);
  }

  @Test
  void sendsEveryPdfAsAFilesPartNamedSoThatAlphabeticalOrderIsTheGivenOrder() {
    List<byte[]> pdfs = new ArrayList<>();
    for (int i = 1; i <= 12; i++) pdfs.add(pdf("doc" + i));

    byte[] merged = merger(baseUrl() + "/").merge(pdfs);

    assertThat(new String(merged, StandardCharsets.US_ASCII)).startsWith("%PDF-");
    assertThat(contentType.get()).startsWith("multipart/form-data; boundary=");
    String body = new String(requestBody.get(), StandardCharsets.ISO_8859_1);
    assertThat(body).endsWith("--\r\n");
    int last = -1;
    for (int i = 1; i <= 12; i++) {
      int at = body.indexOf("name=\"files\"; filename=\"" + String.format("%04d", i) + ".pdf\"");
      assertThat(at).isGreaterThan(last);
      assertThat(body.indexOf("doc" + i + "\r\n")).isGreaterThan(at);
      last = at;
    }
  }

  @Test
  void aNonOkAnswerOrANonPdfBodyIsADependencyFailure() {
    status = 503;
    assertThatThrownBy(() -> merger(baseUrl()).merge(List.of(pdf("a"), pdf("b"))))
        .isInstanceOf(DependencyUnavailableException.class)
        .hasMessageContaining("503");

    status = 200;
    answer = "<html>".getBytes(StandardCharsets.US_ASCII);
    assertThatThrownBy(() -> merger(baseUrl()).merge(List.of(pdf("a"), pdf("b"))))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  @Test
  void anUnreachableServerIsADependencyFailure() {
    String url = baseUrl();
    server.stop(0);

    assertThatThrownBy(() -> merger(url).merge(List.of(pdf("a"))))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  @Test
  void refusesNothingToMerge() {
    assertThatThrownBy(() -> merger(baseUrl()).merge(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
