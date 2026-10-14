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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GotenbergPdfConverterTest {
  private HttpServer server;
  private final AtomicReference<String> contentType = new AtomicReference<>();
  private final AtomicReference<byte[]> requestBody = new AtomicReference<>();
  private volatile int status = 200;
  private volatile byte[] answer = "%PDF-1.7 converted".getBytes(StandardCharsets.US_ASCII);
  private final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(2))
          .build();

  @BeforeEach
  void startFakeGotenberg() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/forms/libreoffice/convert",
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

  private GotenbergPdfConverter converter(String url) {
    return new GotenbergPdfConverter(client, url, Duration.ofSeconds(5));
  }

  @Test
  void postsTheDocxAsTheFilesPartAndReturnsThePdf() {
    byte[] pdf = converter(baseUrl() + "/").toPdf(new byte[] {1, 2, 3, 4});

    assertThat(new String(pdf, StandardCharsets.US_ASCII)).startsWith("%PDF-");
    assertThat(contentType.get()).startsWith("multipart/form-data; boundary=");
    String body = new String(requestBody.get(), StandardCharsets.ISO_8859_1);
    assertThat(body)
        .contains("name=\"files\"; filename=\"form.docx\"")
        .contains("\u0001\u0002\u0003\u0004")
        .endsWith("--\r\n");
  }

  @Test
  void aNonOkAnswerIsADependencyFailure() {
    status = 500;

    assertThatThrownBy(() -> converter(baseUrl()).toPdf(new byte[1]))
        .isInstanceOf(DependencyUnavailableException.class)
        .hasMessageContaining("500");
  }

  @Test
  void aBodyThatIsNotAPdfIsADependencyFailure() {
    answer = "<html>".getBytes(StandardCharsets.US_ASCII);

    assertThatThrownBy(() -> converter(baseUrl()).toPdf(new byte[1]))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  @Test
  void anUnreachableServerIsADependencyFailure() {
    String url = baseUrl();
    server.stop(0);

    assertThatThrownBy(() -> converter(url).toPdf(new byte[1]))
        .isInstanceOf(DependencyUnavailableException.class);
  }

}
