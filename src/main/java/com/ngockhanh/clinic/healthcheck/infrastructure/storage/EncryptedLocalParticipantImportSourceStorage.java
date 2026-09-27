package com.ngockhanh.clinic.healthcheck.infrastructure.storage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ngockhanh.clinic.healthcheck.application.port.ParticipantImportSourceStorage;

@Component
public final class EncryptedLocalParticipantImportSourceStorage implements ParticipantImportSourceStorage {
    private static final int FORMAT_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final Duration RETENTION = Duration.ofDays(7);

    private final Path root;
    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedLocalParticipantImportSourceStorage(
            @Value("${clinic.file-storage.local-root:var/clinic/file-storage}") String root,
            @Value("${clinic.file-storage.encryption-key:}") String encodedKey) {
        this.root = Path.of(root).toAbsolutePath().normalize().resolve("participant-imports").normalize();
        this.encodedKey = encodedKey;
    }

    @Override
    public String store(UUID attachmentId, byte[] content) throws IOException {
        if (attachmentId == null || content == null) throw new IllegalArgumentException("Invalid source file");
        String storageKey = attachmentId + ".enc";
        Path destination = resolve(storageKey);
        Path temporary = root.resolve(attachmentId + ".tmp-" + UUID.randomUUID()).normalize();
        Files.createDirectories(root);
        try {
            Files.write(temporary, encrypt(content), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return storageKey;
    }

    @Override
    public byte[] read(String storageKey) throws IOException {
        try {
            return decrypt(Files.readAllBytes(resolve(storageKey)));
        } catch (GeneralSecurityException failure) {
            throw new IOException("Unable to decrypt participant import source", failure);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    @Scheduled(cron = "0 0 * * * *")
    void removeExpiredSourceFiles() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return;
        Instant expiresBefore = Instant.now().minus(RETENTION);
        try (var files = Files.list(root)) {
            var iterator = files.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (!file.getFileName().toString().endsWith(".enc")
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                FileTime lastModified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS);
                if (lastModified.toInstant().isBefore(expiresBefore)) Files.deleteIfExists(file);
            }
        }
    }

    private byte[] encrypt(byte[] plaintext) throws IOException {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream(1 + iv.length + ciphertext.length);
            try (DataOutputStream output = new DataOutputStream(encoded)) {
                output.writeByte(FORMAT_VERSION);
                output.write(iv);
                output.write(ciphertext);
            }
            return encoded.toByteArray();
        } catch (GeneralSecurityException failure) {
            throw new IOException("Unable to encrypt participant import source", failure);
        }
    }

    private byte[] decrypt(byte[] encoded) throws GeneralSecurityException, IOException {
        if (encoded.length < 1 + IV_LENGTH + TAG_LENGTH_BITS / Byte.SIZE || encoded[0] != FORMAT_VERSION) {
            throw new IOException("Invalid encrypted participant import source");
        }
        byte[] iv = java.util.Arrays.copyOfRange(encoded, 1, 1 + IV_LENGTH);
        byte[] ciphertext = java.util.Arrays.copyOfRange(encoded, 1 + IV_LENGTH, encoded.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    private SecretKeySpec secretKey() {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) throw new IllegalStateException("File encryption key must decode to 32 bytes");
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException invalidKey) {
            throw new IllegalStateException("File encryption key must be Base64 encoded", invalidKey);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || !storageKey.endsWith(".enc")) {
            throw new IllegalArgumentException("Invalid participant source storage key");
        }
        String fileId = storageKey.substring(0, storageKey.length() - 4);
        UUID id;
        try {
            id = UUID.fromString(fileId);
        } catch (IllegalArgumentException invalidId) {
            throw new IllegalArgumentException("Invalid participant source storage key", invalidId);
        }
        if (!storageKey.equals(id + ".enc")) throw new IllegalArgumentException("Invalid participant source storage key");
        Path file = root.resolve(storageKey).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Invalid participant source storage key");
        return file;
    }
}
