package com.ngockhanh.clinic.healthexamination.application.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * SHA-256 fingerprint of everything that defines a Participant import request: the organization,
 * the agreement, the expected agreement version, the parser version, the digest of the workbook
 * bytes and, when the import also schedules, the session. The encoding is length-prefixed and fixed-width, so different requests never share an
 * encoding. The file name and any cell content are not part of it.
 */
public final class ParticipantImportFingerprint {
  private static final byte[] LABEL = "participant-import".getBytes(StandardCharsets.US_ASCII);

  private ParticipantImportFingerprint() {}

  /**
   * Computes the 32-byte fingerprint.
   *
   * @param parserVersion version of the Excel contract the workbook is read with
   */
  public static byte[] of(
      UUID organizationId, UUID agreementId, long rowVersion, int parserVersion, byte[] workbook) {
    return of(organizationId, agreementId, rowVersion, parserVersion, workbook, null);
  }

  /**
   * Computes the 32-byte fingerprint of an import that may also schedule into a session. Without a
   * session the encoding is the same as {@link #of(UUID, UUID, long, int, byte[])}.
   *
   * @param parserVersion version of the Excel contract the workbook is read with
   * @param sessionId session the Participants are scheduled into, or null
   */
  public static byte[] of(
      UUID organizationId,
      UUID agreementId,
      long rowVersion,
      int parserVersion,
      byte[] workbook,
      UUID sessionId) {
    byte[] workbookDigest = sha256().digest(workbook);
    ByteBuffer encoded =
        ByteBuffer.allocate(
            Integer.BYTES
                + LABEL.length
                + 16
                + 16
                + Long.BYTES
                + Integer.BYTES
                + 32
                + (sessionId == null ? 0 : 16));
    encoded.putInt(LABEL.length).put(LABEL);
    encoded
        .putLong(organizationId.getMostSignificantBits())
        .putLong(organizationId.getLeastSignificantBits());
    encoded
        .putLong(agreementId.getMostSignificantBits())
        .putLong(agreementId.getLeastSignificantBits());
    encoded.putLong(rowVersion).putInt(parserVersion).put(workbookDigest);
    if (sessionId != null)
      encoded
          .putLong(sessionId.getMostSignificantBits())
          .putLong(sessionId.getLeastSignificantBits());
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
