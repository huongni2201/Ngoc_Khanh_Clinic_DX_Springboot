package com.ngockhanh.clinic.healthexamination.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantImportFingerprintTest {
  private final UUID org = new UUID(0, 1);
  private final UUID agreement = new UUID(0, 2);
  private final byte[] bytes = {1, 2, 3};

  @Test
  void isAStableThirtyTwoByteDigest() {
    byte[] first = ParticipantImportFingerprint.of(org, agreement, 4, 1, bytes);
    assertThat(first)
        .hasSize(32)
        .isEqualTo(ParticipantImportFingerprint.of(org, agreement, 4, 1, bytes.clone()));
  }

  @Test
  void everyComponentChangesIt() {
    byte[] base = ParticipantImportFingerprint.of(org, agreement, 4, 1, bytes);
    assertThat(ParticipantImportFingerprint.of(new UUID(0, 9), agreement, 4, 1, bytes))
        .isNotEqualTo(base);
    assertThat(ParticipantImportFingerprint.of(org, new UUID(0, 9), 4, 1, bytes))
        .isNotEqualTo(base);
    assertThat(ParticipantImportFingerprint.of(org, agreement, 5, 1, bytes)).isNotEqualTo(base);
    assertThat(ParticipantImportFingerprint.of(org, agreement, 4, 2, bytes)).isNotEqualTo(base);
    assertThat(ParticipantImportFingerprint.of(org, agreement, 4, 1, new byte[] {1, 2, 4}))
        .isNotEqualTo(base);
  }

  @Test
  void aRequestWithoutASessionEncodesAsItAlwaysDid() {
    assertThat(ParticipantImportFingerprint.of(org, agreement, 4, 1, bytes, null))
        .isEqualTo(ParticipantImportFingerprint.of(org, agreement, 4, 1, bytes));
  }

  @Test
  void theSessionChangesIt() {
    UUID session = new UUID(0, 7);
    byte[] withSession = ParticipantImportFingerprint.of(org, agreement, 4, 1, bytes, session);
    assertThat(withSession)
        .hasSize(32)
        .isNotEqualTo(ParticipantImportFingerprint.of(org, agreement, 4, 1, bytes))
        .isNotEqualTo(
            ParticipantImportFingerprint.of(org, agreement, 4, 1, bytes, new UUID(0, 8)))
        .isEqualTo(ParticipantImportFingerprint.of(org, agreement, 4, 1, bytes, session));
  }
}
