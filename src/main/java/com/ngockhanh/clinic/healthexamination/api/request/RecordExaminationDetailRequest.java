package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.RecordExaminationDetailCommand;
import com.ngockhanh.clinic.healthexamination.application.command.RecordExaminationDetailCommand.Attendance;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

/**
 * Body of recording the examination details of one Participant by hand.
 *
 * @param attendanceStatus {@code ATTENDED} or {@code ABSENT}; {@code UNCONFIRMED} is rejected
 * @param actualExaminationDate required when attended, must be absent when absent
 * @param performedBatchServiceIds batch services performed, without duplicates; at least one when
 *     attended, none when absent
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record RecordExaminationDetailRequest(
    @NotNull String attendanceStatus,
    LocalDate actualExaminationDate,
    @NotNull @Size(max = MAX_SERVICES) List<@NotNull UUID> performedBatchServiceIds,
    @NotNull @PositiveOrZero Long rowVersion) {
  static final int MAX_SERVICES = 100;

  /**
   * Maps the validated body to the application command. Only the shape of the body is checked here;
   * the rules that tie the date and the services to the attendance belong to the use case.
   *
   * @throws ApplicationException {@code INVALID_INPUT} when the status is not {@code ATTENDED} or
   *     {@code ABSENT}, or a service is listed twice
   */
  public RecordExaminationDetailCommand toCommand() {
    Attendance attendance =
        switch (attendanceStatus) {
          case "ATTENDED" -> Attendance.ATTENDED;
          case "ABSENT" -> Attendance.ABSENT;
          default -> throw invalid("attendanceStatus must be ATTENDED or ABSENT");
        };
    Set<UUID> services = new HashSet<>(performedBatchServiceIds);
    if (services.size() != performedBatchServiceIds.size())
      throw invalid("performedBatchServiceIds must not contain duplicates");
    return new RecordExaminationDetailCommand(
        attendance, actualExaminationDate, services, rowVersion);
  }

  private static ApplicationException invalid(String message) {
    return new ApplicationException(ApplicationException.Type.INVALID_INPUT, message);
  }
}
