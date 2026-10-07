package com.ngockhanh.clinic.healthexamination.infrastructure.configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the import limits of the Participant and examination detail Excel imports and configures Apache POI's zip-bomb protection once at startup.
 * POI keeps these limits in static fields, so they are never changed per request.
 */
@Configuration
@EnableConfigurationProperties({
  ParticipantImportProperties.class,
  ExaminationDetailImportProperties.class
})
@RequiredArgsConstructor
public class ParticipantExcelConfiguration {
  private final ParticipantImportProperties properties;

  @PostConstruct
  void configurePoi() {
    ZipSecureFile.setMinInflateRatio(0.01d);
    ZipSecureFile.setMaxEntrySize(properties.maxEntryBytes());
    ZipSecureFile.setMaxFileCount(properties.maxZipEntries());
    ZipSecureFile.setMaxTextSize(properties.maxEntryBytes());
  }
}
