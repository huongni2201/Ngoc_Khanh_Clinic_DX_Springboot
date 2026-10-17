package com.ngockhanh.clinic.healthexamination.application.command;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Input for creating a session.
 *
 * @param agreementId the only agreement the session serves, or null when it is shared by every
 *     agreement of the organization
 * @param activity {@code ALL}, {@code SAMPLING} or {@code EXAMINATION}
 * @param siteType {@code CLINIC} or {@code ORGANIZATION_SITE}
 * @param siteAddress address of the organization site; ignored for the clinic
 */
@Builder
public record CreateSessionCommand(
    UUID agreementId,
    LocalDate sessionDate,
    String activity,
    String siteType,
    String siteAddress) {}
