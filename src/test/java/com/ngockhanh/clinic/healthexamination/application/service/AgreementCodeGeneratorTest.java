package com.ngockhanh.clinic.healthexamination.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AgreementCodeGeneratorTest {
  private final HealthExaminationAgreementRepository agreements =
      mock(HealthExaminationAgreementRepository.class);

  private AgreementCodeGenerator at(String instant) {
    return new AgreementCodeGenerator(
        agreements, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
  }

  @Test
  void firstCodeOfTheYearStartsAtOne() {
    assertThat(at("2026-10-08T03:00:00Z").next()).isEqualTo("KSK-2026-001");

    verify(agreements).highestCodeSequence("KSK-2026-");
  }

  @Test
  void continuesAfterTheHighestNumberAlreadyUsed() {
    when(agreements.highestCodeSequence("KSK-2026-")).thenReturn(9L);

    assertThat(at("2026-10-08T03:00:00Z").next()).isEqualTo("KSK-2026-010");
  }

  @Test
  void growsPastThreeDigitsWithoutTruncating() {
    when(agreements.highestCodeSequence("KSK-2026-")).thenReturn(999L);

    assertThat(at("2026-10-08T03:00:00Z").next()).isEqualTo("KSK-2026-1000");
  }

  @Test
  void theYearFollowsTheBusinessTimeZone() {
    // 17:30 UTC on 31 Dec is already 1 Jan in Vietnam (UTC+7).
    assertThat(at("2026-12-31T17:30:00Z").next()).isEqualTo("KSK-2027-001");
    verify(agreements).highestCodeSequence("KSK-2027-");
  }
}
