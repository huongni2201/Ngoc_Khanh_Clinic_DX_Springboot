package com.ngockhanh.clinic.healthexamination.application.command;

import java.time.LocalDate;
import lombok.Builder;

/**
 * Input for changing the date, activity and site of an open session.
 *
 * @param rowVersion session version the caller last read; must match the stored version
 */
@Builder
public record UpdateSessionCommand(
    LocalDate sessionDate, String activity, String siteType, String siteAddress, Long rowVersion) {}
