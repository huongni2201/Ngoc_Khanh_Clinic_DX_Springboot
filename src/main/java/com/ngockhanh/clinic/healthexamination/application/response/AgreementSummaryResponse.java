package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository.AgreementSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record AgreementSummaryResponse(
    UUID id,
    String agreementCode,
    String agreementName,
    LocalDate validFrom,
    LocalDate validTo,
    String status,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {
  public static AgreementSummaryResponse from(AgreementSummary a) {
    return AgreementSummaryResponse.builder()
        .id(a.id())
        .agreementCode(a.agreementCode())
        .agreementName(a.agreementName())
        .validFrom(a.validFrom())
        .validTo(a.validTo())
        .status(a.status().name())
        .createdAt(a.createdAt())
        .updatedAt(a.updatedAt())
        .rowVersion(a.rowVersion())
        .build();
  }
}
