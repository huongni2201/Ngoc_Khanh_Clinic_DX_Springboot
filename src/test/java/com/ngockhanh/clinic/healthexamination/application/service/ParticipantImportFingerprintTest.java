package com.ngockhanh.clinic.healthexamination.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantImportFingerprintTest {
  private final UUID org = new UUID(0, 1);
  private final UUID batch = new UUID(0, 2);
  private final byte[] bytes = {1, 2, 3};

  @Test
  void isAStableThirtyTwoByteDigest() {
    byte[] first = ParticipantImportFingerprint.of(org, batch, 4, 1, bytes);
    assertThat(first).hasSize(32).isEqualTo(ParticipantImportFingerprint.of(org, batch, 4, 1, bytes.clone()));
  }

  @Test
  void everyComponentChangesIt() {
    byte[] base = ParticipantImportFingerprint.of(org, batch, 4, 1, bytes);
    assertThat(ParticipantImportFingerprint.of(new UUID(0, 9), batch, 4, 1, bytes)).isNotEqualTo(base);
    assertThat(ParticipantImportFingerprint.of(org, new UUID(0, 9), 4, 1, bytes)).isNotEqualTo(base);
    assertThat(ParticipantImportFingerprint.of(org, batch, 5, 1, bytes)).isNotEqualTo(base);
    assertThat(ParticipantImportFingerprint.of(org, batch, 4, 2, bytes)).isNotEqualTo(base);
    assertThat(ParticipantImportFingerprint.of(org, batch, 4, 1, new byte[] {1, 2, 4})).isNotEqualTo(base);
  }
}
