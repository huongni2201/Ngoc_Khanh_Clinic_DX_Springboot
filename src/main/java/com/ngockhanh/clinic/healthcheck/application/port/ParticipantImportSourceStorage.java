package com.ngockhanh.clinic.healthcheck.application.port;

import java.io.IOException;
import java.util.UUID;

public interface ParticipantImportSourceStorage {
    String store(UUID attachmentId, byte[] content) throws IOException;
    byte[] read(String storageKey) throws IOException;
    void delete(String storageKey) throws IOException;
}
