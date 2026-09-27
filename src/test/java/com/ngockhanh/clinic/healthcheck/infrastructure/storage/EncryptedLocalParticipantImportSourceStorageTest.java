package com.ngockhanh.clinic.healthcheck.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncryptedLocalParticipantImportSourceStorageTest {
    @TempDir
    Path tempDir;

    private final String encodedKey = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsSourceAndReadsItBack() throws Exception {
        var storage = new EncryptedLocalParticipantImportSourceStorage(tempDir.toString(), encodedKey);
        byte[] plaintext = "participant-cccd-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String key = storage.store(UUID.randomUUID(), plaintext);
        Path storedFile = tempDir.resolve("participant-imports").resolve(key);

        assertThat(Files.readAllBytes(storedFile)).isNotEqualTo(plaintext);
        assertThat(storage.read(key)).isEqualTo(plaintext);
    }

    @Test
    void rejectsTamperedSource() throws Exception {
        var storage = new EncryptedLocalParticipantImportSourceStorage(tempDir.toString(), encodedKey);
        String key = storage.store(UUID.randomUUID(), new byte[] {1, 2, 3});
        Path storedFile = tempDir.resolve("participant-imports").resolve(key);
        byte[] encrypted = Files.readAllBytes(storedFile);
        encrypted[encrypted.length - 1] ^= 1;
        Files.write(storedFile, encrypted);

        assertThatThrownBy(() -> storage.read(key)).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void deletesOnlySourceFilesOlderThanSevenDays() throws Exception {
        var storage = new EncryptedLocalParticipantImportSourceStorage(tempDir.toString(), encodedKey);
        String expiredKey = storage.store(UUID.randomUUID(), new byte[] {1});
        String recentKey = storage.store(UUID.randomUUID(), new byte[] {2});
        Path expiredFile = tempDir.resolve("participant-imports").resolve(expiredKey);
        Path recentFile = tempDir.resolve("participant-imports").resolve(recentKey);
        Files.setLastModifiedTime(expiredFile, FileTime.from(Instant.now().minus(Duration.ofDays(8))));

        storage.removeExpiredSourceFiles();

        assertThat(Files.exists(expiredFile)).isFalse();
        assertThat(Files.exists(recentFile)).isTrue();
    }
}
