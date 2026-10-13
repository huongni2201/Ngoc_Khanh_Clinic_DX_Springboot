package com.ngockhanh.clinic.document.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code nkc.document.*} settings of the document module.
 *
 * @param gotenberg PDF converter settings
 * @param storage file storage settings
 * @param jobs background print job settings
 */
@ConfigurationProperties("nkc.document")
public record DocumentProperties(Gotenberg gotenberg, Storage storage, Jobs jobs) {
  public DocumentProperties {
    gotenberg = gotenberg == null ? new Gotenberg(null, null, null) : gotenberg;
    storage = storage == null ? new Storage(null) : storage;
    jobs = jobs == null ? new Jobs(null, null, null, null, null, null, null) : jobs;
  }

  /**
   * @param url base URL of the Gotenberg service, default {@code http://localhost:3000}
   * @param timeout how long one conversion may take, default 15 seconds
   * @param mergeTimeout how long merging one print part may take, default 90 seconds
   */
  public record Gotenberg(String url, Duration timeout, Duration mergeTimeout) {
    public Gotenberg {
      url = url == null || url.isBlank() ? "http://localhost:3000" : url.strip();
      timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
      mergeTimeout = mergeTimeout == null ? Duration.ofSeconds(90) : mergeTimeout;
    }
  }

  /**
   * @param localRoot directory of the {@code LOCAL} provider; blank leaves that provider off
   */
  public record Storage(String localRoot) {
    public Storage {
      localRoot = localRoot == null ? "" : localRoot.strip();
    }
  }

  /**
   * @param maxRecords most records one job may print, default 1000
   * @param partSize most records in one merged PDF part, default 200
   * @param chunkSize records between two progress saves, default 25
   * @param lease how long a worker may keep a job without saving progress, default 5 minutes
   * @param maxAttempts how many times a job may be taken, default 3
   * @param retention how long finished files are kept, default 24 hours
   * @param maxActivePerRequester jobs one account may have waiting or running, default 3
   */
  public record Jobs(
      Integer maxRecords,
      Integer partSize,
      Integer chunkSize,
      Duration lease,
      Integer maxAttempts,
      Duration retention,
      Integer maxActivePerRequester) {
    public Jobs {
      maxRecords = maxRecords == null ? 1_000 : maxRecords;
      partSize = partSize == null ? 200 : partSize;
      chunkSize = chunkSize == null ? 25 : chunkSize;
      lease = lease == null ? Duration.ofMinutes(5) : lease;
      maxAttempts = maxAttempts == null ? 3 : maxAttempts;
      retention = retention == null ? Duration.ofHours(24) : retention;
      maxActivePerRequester = maxActivePerRequester == null ? 3 : maxActivePerRequester;
    }
  }
}
