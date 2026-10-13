package com.ngockhanh.clinic.document.application.port;

/** Reads, writes and deletes the bytes of stored document files. */
public interface DocumentStorage {
  /**
   * Reads a file and checks it against the checksum recorded for it.
   *
   * @param provider {@code CLASSPATH} or {@code LOCAL}
   * @param key location inside the provider
   * @param sha256 expected SHA-256 of the content, or {@code null} to skip the check
   * @return the file content
   * @throws IllegalStateException when the file is missing, unreadable or does not match the
   *     checksum
   */
  byte[] read(String provider, String key, byte[] sha256);

  /** Whether files can be written to the provider (only {@code LOCAL}, and only when configured). */
  boolean writable(String provider);

  /**
   * Writes a file, replacing any file with the same key.
   *
   * @return the SHA-256 of the content
   * @throws IllegalStateException when the provider cannot be written or the write fails
   * @throws IllegalArgumentException when the key would leave the provider's root
   */
  byte[] write(String provider, String key, byte[] content);

  /**
   * Deletes a file; a file that is already gone is not an error.
   *
   * @throws IllegalStateException when the provider cannot be written or the delete fails
   */
  void delete(String provider, String key);
}
