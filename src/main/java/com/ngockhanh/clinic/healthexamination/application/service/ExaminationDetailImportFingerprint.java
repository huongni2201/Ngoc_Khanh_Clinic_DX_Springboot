package com.ngockhanh.clinic.healthexamination.application.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * SHA-256 fingerprint of everything that defines an examination detail import request: the
 * organization, the batch, the parser version and the digest of the workbook bytes. The encoding is
 * length-prefixed and fixed-width, so different requests never share an encoding. The file name and
 * any cell content are not part of it.
 */
public final class ExaminationDetailImportFingerprint {
  private static final byte[] LABEL =
      "examination-detail-import".getBytes(StandardCharsets.US_ASCII);

  private ExaminationDetailImportFingerprint() {}

  /**
   * Computes the 32-byte fingerprint.
   *
   * @param parserVersion version of the Excel contract the workbook is read with
   */
  public static byte[] of(UUID organizationId, UUID batchId, int parserVersion, byte[] workbook) {
    byte[] workbookDigest = sha256().digest(workbook);
    ByteBuffer encoded =
        ByteBuffer.allocate(Integer.BYTES + LABEL.length + 16 + 16 + Integer.BYTES + 32);
    encoded.putInt(LABEL.length).put(LABEL);
    encoded
        .putLong(organizationId.getMostSignificantBits())
        .putLong(organizationId.getLeastSignificantBits());
    encoded.putLong(batchId.getMostSignificantBits()).putLong(batchId.getLeastSignificantBits());
    encoded.putInt(parserVersion).put(workbookDigest);
    return sha256().digest(encoded.array());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
