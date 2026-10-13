package com.ngockhanh.clinic.document.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfiguredDocumentStorageTest {
  private static final String STARTER =
      "document-templates/health-exam-periodic-form-blank/starter-v1.docx";

  @TempDir Path root;

  private ConfiguredDocumentStorage storage() {
    return new ConfiguredDocumentStorage(root, getClass().getClassLoader());
  }

  private static byte[] sha256(String text) throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void readsALocalFileAndChecksItsChecksum() throws Exception {
    Files.createDirectories(root.resolve("sub"));
    Files.writeString(root.resolve("sub/f.bin"), "hello");

    assertThat(new String(storage().read("LOCAL", "sub/f.bin", sha256("hello")), StandardCharsets.UTF_8))
        .isEqualTo("hello");
    assertThatThrownBy(() -> storage().read("LOCAL", "sub/f.bin", new byte[32]))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("checksum");
  }

  @Test
  void readsTheStarterTemplateFromTheClasspath() throws IOException {
    assertThat(storage().read("CLASSPATH", STARTER, null)).startsWith((byte) 'P', (byte) 'K');
  }

  @Test
  void aKeyCannotLeaveItsRoot() {
    assertThatThrownBy(() -> storage().read("LOCAL", "../outside", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage().read("CLASSPATH", "../outside", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage().read("CLASSPATH", "/etc/passwd", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reportsMissingFilesUnsupportedProvidersAndAnUnconfiguredLocalRoot() {
    assertThatThrownBy(() -> storage().read("CLASSPATH", "nope/missing.docx", null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> storage().read("LOCAL", "missing.bin", null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> storage().read("S3", "key", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("S3");
    assertThatThrownBy(
            () ->
                new ConfiguredDocumentStorage(null, getClass().getClassLoader())
                    .read("LOCAL", "a", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not configured");
  }

  @Test
  void onlyAConfiguredLocalRootCanBeWritten() {
    assertThat(storage().writable("LOCAL")).isTrue();
    assertThat(storage().writable("CLASSPATH")).isFalse();
    assertThat(new ConfiguredDocumentStorage(null, getClass().getClassLoader()).writable("LOCAL"))
        .isFalse();
    assertThatThrownBy(() -> storage().write("CLASSPATH", "a.pdf", new byte[1]))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new ConfiguredDocumentStorage(null, getClass().getClassLoader())
                    .write("LOCAL", "a.pdf", new byte[1]))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void writesAFileInNewFoldersAndReturnsItsChecksum() throws Exception {
    byte[] checksum = storage().write("LOCAL", "render-jobs/2026/10/job-1.pdf", "pdf".getBytes(StandardCharsets.UTF_8));

    assertThat(checksum).isEqualTo(sha256("pdf"));
    assertThat(Files.readString(root.resolve("render-jobs/2026/10/job-1.pdf"))).isEqualTo("pdf");
    assertThat(storage().read("LOCAL", "render-jobs/2026/10/job-1.pdf", checksum)).isEqualTo("pdf".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void writingTheSameKeyReplacesTheFileAndLeavesNoTemporaryFile() throws Exception {
    storage().write("LOCAL", "out/a.pdf", "one".getBytes(StandardCharsets.UTF_8));
    storage().write("LOCAL", "out/a.pdf", "two".getBytes(StandardCharsets.UTF_8));

    assertThat(Files.readString(root.resolve("out/a.pdf"))).isEqualTo("two");
    try (var files = Files.list(root.resolve("out"))) {
      assertThat(files.map(f -> f.getFileName().toString()).toList()).containsExactly("a.pdf");
    }
  }

  @Test
  void readsALargeMergedPartThatNoTemplateWouldBe() throws Exception {
    byte[] large = new byte[ConfiguredDocumentStorage.MAX_BYTES + 1024];
    storage().write("LOCAL", "out/large.pdf", large);

    assertThat(storage().read("LOCAL", "out/large.pdf", null).length).isEqualTo(large.length);
  }

  @Test
  void deletesAFileAndIgnoresOneThatIsAlreadyGone() {
    storage().write("LOCAL", "out/a.pdf", new byte[] {1});

    storage().delete("LOCAL", "out/a.pdf");
    storage().delete("LOCAL", "out/a.pdf");

    assertThat(Files.exists(root.resolve("out/a.pdf"))).isFalse();
  }

  @Test
  void writesAndDeletesCannotLeaveTheRootOrTouchItItself() {
    for (String key : new String[] {"../outside.pdf", "a/../../outside.pdf", "", " "}) {
      assertThatThrownBy(() -> storage().write("LOCAL", key, new byte[1]))
          .as(key)
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> storage().delete("LOCAL", key))
          .as(key)
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> storage().delete("LOCAL", "."))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage().write("LOCAL", "a.pdf", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
