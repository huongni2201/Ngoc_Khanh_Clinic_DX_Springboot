package com.ngockhanh.clinic.healthexamination.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class EncryptedLocalImportFileStorageTest {
    private Path testRoot;

    @AfterEach
    void removeTestFiles() throws Exception {
        if (testRoot == null || !Files.exists(testRoot)) return;
        try (Stream<Path> paths = Files.walk(testRoot)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void storesEncryptedBytesAndCanReadAndDeleteThem() throws Exception {
        byte[] key = new byte[32];
        byte[] content = "synthetic-roster-content".getBytes(StandardCharsets.UTF_8);
        var storage = new EncryptedLocalImportFileStorage(testRoot(), Base64.getEncoder().encodeToString(key));

        var stored = storage.store(UUID.randomUUID(), new ByteArrayInputStream(content), 1024);

        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(HexFormat.of().formatHex(Files.readAllBytes(testRoot.resolve(stored.storageKey()))))
                .doesNotContain(HexFormat.of().formatHex(content));
        try (var decrypted = storage.open(stored.storageKey())) {
            assertThat(decrypted.readAllBytes()).isEqualTo(content);
        }
        storage.delete(stored.storageKey());
        assertThat(Files.exists(testRoot.resolve(stored.storageKey()))).isFalse();
    }

    @Test
    void rejectsFilesThatExceedTheConfiguredLimit() throws Exception {
        var storage = new EncryptedLocalImportFileStorage(testRoot(),
                Base64.getEncoder().encodeToString(new byte[32]));

        assertThatThrownBy(() -> storage.store(UUID.randomUUID(),
                new ByteArrayInputStream(new byte[20]), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsClosedWhenEncryptionKeyIsNotConfigured() throws Exception {
        var storage = new EncryptedLocalImportFileStorage(testRoot(), "");

        assertThatThrownBy(() -> storage.store(UUID.randomUUID(),
                new ByteArrayInputStream(new byte[]{1}), 10))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void springCreatesStorageFromConfiguredProperties() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("storage-test", Map.of(
                    "clinic.file-storage.local-root", testRoot().toString(),
                    "clinic.file-storage.encryption-key", Base64.getEncoder().encodeToString(new byte[32]))));
            context.register(EncryptedLocalImportFileStorage.class);
            context.refresh();
            byte[] content = "configured-storage".getBytes(StandardCharsets.UTF_8);
            var storage = context.getBean(EncryptedLocalImportFileStorage.class);
            var stored = storage.store(UUID.randomUUID(), new ByteArrayInputStream(content), 1024);
            try (var decrypted = storage.open(stored.storageKey())) {
                assertThat(decrypted.readAllBytes()).isEqualTo(content);
            }
        }
    }

    private Path testRoot() throws Exception {
        if (testRoot == null) {
            testRoot = Path.of("target", "import-storage-tests", UUID.randomUUID().toString()).toAbsolutePath();
            Files.createDirectories(testRoot);
        }
        return testRoot;
    }
}
