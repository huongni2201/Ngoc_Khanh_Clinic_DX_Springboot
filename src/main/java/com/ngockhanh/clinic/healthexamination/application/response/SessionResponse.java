package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository.SessionSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * One session with the number of Participants in it. The counts are computed when the session is
 * read and are for reference: a session has no capacity.
 *
 * @param agreementId the only agreement the session serves, or null when it is shared
 * @param assignedCount Participants expected or attended
 */
@Builder
public record SessionResponse(
    UUID id,
    UUID organizationId,
    UUID agreementId,
    LocalDate sessionDate,
    String activity,
    String siteType,
    String siteAddress,
    String status,
    long assignedCount,
    long attendedCount,
    long noShowCount,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {
  public static SessionResponse from(SessionSummary s) {
    return SessionResponse.builder()
        .id(s.id())
        .organizationId(s.organizationId())
        .agreementId(s.agreementId())
        .sessionDate(s.sessionDate())
        .activity(s.activity().name())
        .siteType(s.siteType().name())
        .siteAddress(s.siteAddress())
        .status(s.status().name())
        .assignedCount(s.assignedCount())
        .attendedCount(s.attendedCount())
        .noShowCount(s.noShowCount())
        .createdAt(s.createdAt())
        .updatedAt(s.updatedAt())
        .rowVersion(s.rowVersion())
        .build();
  }
}
