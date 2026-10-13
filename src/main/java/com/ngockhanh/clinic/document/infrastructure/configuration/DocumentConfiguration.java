package com.ngockhanh.clinic.document.infrastructure.configuration;

import com.ngockhanh.clinic.document.application.port.BarcodeImageGenerator;
import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.DocxTemplateEngine;
import com.ngockhanh.clinic.document.application.port.PdfConverter;
import com.ngockhanh.clinic.document.application.port.PdfMerger;
import com.ngockhanh.clinic.document.application.service.RenderJobSettings;
import com.ngockhanh.clinic.document.infrastructure.rendering.Code128BarcodeGenerator;
import com.ngockhanh.clinic.document.infrastructure.rendering.DomDocxTemplateEngine;
import com.ngockhanh.clinic.document.infrastructure.rendering.GotenbergPdfConverter;
import com.ngockhanh.clinic.document.infrastructure.rendering.GotenbergPdfMerger;
import com.ngockhanh.clinic.document.infrastructure.storage.ConfiguredDocumentStorage;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the document module adapters from {@code nkc.document.*}. */
@Configuration
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentConfiguration {
  @Bean
  DocumentStorage documentStorage(DocumentProperties properties) {
    String root = properties.storage().localRoot();
    return new ConfiguredDocumentStorage(
        root.isEmpty() ? null : Path.of(root), DocumentConfiguration.class.getClassLoader());
  }

  @Bean
  DocxTemplateEngine docxTemplateEngine() {
    return new DomDocxTemplateEngine();
  }

  @Bean
  BarcodeImageGenerator barcodeImageGenerator() {
    return new Code128BarcodeGenerator();
  }

  @Bean
  PdfConverter pdfConverter(DocumentProperties properties) {
    return new GotenbergPdfConverter(
        gotenbergClient(), properties.gotenberg().url(), properties.gotenberg().timeout());
  }

  @Bean
  PdfMerger pdfMerger(DocumentProperties properties) {
    return new GotenbergPdfMerger(
        gotenbergClient(), properties.gotenberg().url(), properties.gotenberg().mergeTimeout());
  }

  @Bean
  RenderJobSettings renderJobSettings(DocumentProperties properties) {
    var jobs = properties.jobs();
    return new RenderJobSettings(
        jobs.maxRecords(),
        jobs.partSize(),
        jobs.chunkSize(),
        jobs.lease(),
        jobs.maxAttempts(),
        jobs.retention(),
        jobs.maxActivePerRequester());
  }

  private static HttpClient gotenbergClient() {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(3))
        .build();
  }
}
