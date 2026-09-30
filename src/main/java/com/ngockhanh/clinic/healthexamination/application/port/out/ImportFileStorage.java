package com.ngockhanh.clinic.healthexamination.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface ImportFileStorage {
    StoredFile store(UUID importId, InputStream plaintext, long maxBytes) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;

    record StoredFile(String storageKey, long sizeBytes, String sha256) {
    }
}
