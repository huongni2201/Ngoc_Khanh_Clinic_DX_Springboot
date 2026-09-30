package com.ngockhanh.clinic.healthexamination.infrastructure.storage;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportFileStorage;

@Component
public final class EncryptedLocalImportFileStorage implements ImportFileStorage {
    private static final byte[] MAGIC = {'N', 'K', 'C', '1'};
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final Pattern STORAGE_KEY = Pattern.compile(
            "health-examination-imports/[0-9a-fA-F-]{36}\\.gcm");

    private final Path root;
    private final String base64EncryptionKey;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public EncryptedLocalImportFileStorage(
            @Value("${clinic.file-storage.local-root}") String root,
            @Value("${clinic.file-storage.encryption-key:}") String base64EncryptionKey) {
        this(Path.of(root), base64EncryptionKey);
    }

    EncryptedLocalImportFileStorage(Path root, String base64EncryptionKey) {
        this.root = root.toAbsolutePath().normalize();
        this.base64EncryptionKey = base64EncryptionKey;
    }

    @Override
    public StoredFile store(UUID importId, InputStream plaintext, long maxBytes) throws IOException {
        if (importId == null || plaintext == null || maxBytes < 1) {
            throw new IllegalArgumentException("Import file metadata is required");
        }
        SecretKeySpec key = encryptionKey();
        String storageKey = "health-examination-imports/" + importId + ".gcm";
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        MessageDigest digest = sha256();
        long size = 0;

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            try (OutputStream file = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                file.write(MAGIC);
                file.write(iv);
                try (CipherOutputStream encrypted = new CipherOutputStream(file, cipher)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = plaintext.read(buffer)) != -1) {
                        size += read;
                        if (size > maxBytes) throw new IllegalArgumentException("Import file exceeds configured size");
                        digest.update(buffer, 0, read);
                        encrypted.write(buffer, 0, read);
                    }
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target);
            }
            return new StoredFile(storageKey, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        } catch (Exception failure) {
            Files.deleteIfExists(temporary);
            throw new IOException("Unable to store encrypted import file", failure);
        }
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        Path source = resolve(storageKey);
        DataInputStream input = new DataInputStream(Files.newInputStream(source));
        try {
            byte[] magic = input.readNBytes(MAGIC.length);
            if (!MessageDigest.isEqual(MAGIC, magic)) throw new IOException("Invalid encrypted import file");
            byte[] iv = input.readNBytes(IV_LENGTH);
            if (iv.length != IV_LENGTH) throw new IOException("Invalid encrypted import file");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new CipherInputStream(input, cipher);
        } catch (Exception failure) {
            input.close();
            if (failure instanceof IOException ioFailure) throw ioFailure;
            throw new IOException("Unable to open encrypted import file", failure);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) throws IOException {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches()) {
            throw new IOException("Invalid import storage key");
        }
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) throw new IOException("Invalid import storage key");
        return path;
    }

    private SecretKeySpec encryptionKey() {
        if (base64EncryptionKey == null || base64EncryptionKey.isBlank()) {
            throw new IllegalStateException("Import file encryption key is not configured");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64EncryptionKey);
            if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
                throw new IllegalStateException("Import file encryption key has an unsupported length");
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Import file encryption key is invalid", failure);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}
