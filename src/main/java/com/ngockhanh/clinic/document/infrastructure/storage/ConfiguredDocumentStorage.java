package com.ngockhanh.clinic.document.infrastructure.storage;

import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Reads document files from the application classpath (starter templates) or a local directory, and
 * writes and deletes files in that directory (temporary print output). A key can never leave its
 * root, and a recorded checksum must match the content.
 */
public class ConfiguredDocumentStorage implements DocumentStorage {
  /** Largest classpath file accepted; a template is far smaller. */
  static final int MAX_BYTES = 20 * 1024 * 1024;

  /** Largest local file accepted; a merged print part of a few hundred forms is tens of MiB. */
  static final int MAX_LOCAL_BYTES = 256 * 1024 * 1024;

  private final Path localRoot;
  private final ClassLoader classLoader;

  /**
   * @param localRoot directory of the {@code LOCAL} provider, or {@code null} when it is not
   *     configured
   * @param classLoader loader of the {@code CLASSPATH} provider
   */
  public ConfiguredDocumentStorage(Path localRoot, ClassLoader classLoader) {
    this.localRoot = localRoot == null ? null : localRoot.toAbsolutePath().normalize();
    this.classLoader = classLoader;
  }

  @Override
  public byte[] read(String provider, String key, byte[] sha256) {
    if (provider == null || key == null || key.isBlank())
      throw new IllegalArgumentException("Storage provider and key are required");
    byte[] content =
        switch (provider) {
          case "CLASSPATH" -> readClasspath(key);
          case "LOCAL" -> readLocal(key);
          default -> throw new IllegalStateException("Storage provider is not supported: " + provider);
        };
    if (sha256 != null && !MessageDigest.isEqual(sha256, sha256(content)))
      throw new IllegalStateException("Stored file does not match its checksum: " + key);
    return content;
  }

  private byte[] readClasspath(String key) {
    if (key.startsWith("/") || key.contains("..") || key.contains("\\"))
      throw new IllegalArgumentException("Invalid classpath key: " + key);
    try (InputStream in = classLoader.getResourceAsStream(key)) {
      if (in == null) throw new IllegalStateException("Stored file is missing: " + key);
      return limited(in, key, MAX_BYTES);
    } catch (IOException e) {
      throw new IllegalStateException("Stored file cannot be read: " + key, e);
    }
  }

  private byte[] readLocal(String key) {
    if (localRoot == null) throw new IllegalStateException("Local document storage is not configured");
    Path file = localRoot.resolve(key).normalize();
    if (!file.startsWith(localRoot)) throw new IllegalArgumentException("Invalid storage key: " + key);
    try (InputStream in = Files.newInputStream(file)) {
      return limited(in, key, MAX_LOCAL_BYTES);
    } catch (IOException e) {
      throw new IllegalStateException("Stored file cannot be read: " + key, e);
    }
  }

  private static byte[] limited(InputStream in, String key, int max) throws IOException {
    byte[] content = in.readNBytes(max + 1);
    if (content.length > max) throw new IllegalStateException("Stored file is too large: " + key);
    return content;
  }

  @Override
  public boolean writable(String provider) {
    return "LOCAL".equals(provider) && localRoot != null;
  }

  @Override
  public byte[] write(String provider, String key, byte[] content) {
    Path file = writableFile(provider, key);
    if (content == null || content.length > MAX_LOCAL_BYTES)
      throw new IllegalArgumentException("Invalid file content");
    try {
      Files.createDirectories(file.getParent());
      Path temp = Files.createTempFile(file.getParent(), ".part-", ".tmp");
      try {
        Files.write(temp, content);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(temp);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Stored file cannot be written: " + key, e);
    }
    return sha256(content);
  }

  @Override
  public void delete(String provider, String key) {
    Path file = writableFile(provider, key);
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new IllegalStateException("Stored file cannot be deleted: " + key, e);
    }
  }

  private Path writableFile(String provider, String key) {
    if (!writable(provider))
      throw new IllegalStateException("Storage provider cannot be written: " + provider);
    if (key == null || key.isBlank()) throw new IllegalArgumentException("Storage key is required");
    Path file = localRoot.resolve(key).normalize();
    if (!file.startsWith(localRoot) || file.equals(localRoot))
      throw new IllegalArgumentException("Invalid storage key: " + key);
    return file;
  }

  private static byte[] sha256(byte[] content) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(content);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
