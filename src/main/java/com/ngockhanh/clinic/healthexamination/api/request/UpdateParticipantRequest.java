package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.UpdateParticipantCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Body for editing one Participant. It is a full replacement of the roster fields and the
 * examination day. {@code rowVersion} is the Participant version the client last read.
 */
@Builder
public record UpdateParticipantRequest(
    @NotBlank @Size(max = 200) String fullName,
    @NotNull LocalDate dateOfBirth,
    @NotNull @Pattern(regexp = "MALE|FEMALE|OTHER|UNKNOWN") String sex,
    @NotBlank @Pattern(regexp = "\\d{1,20}") String identificationNumber,
    LocalDate identificationIssueDate,
    @Size(max = 500) String identificationIssuePlace,
    @Size(max = 500) String ethnicity,
    @Size(max = 500) String phone,
    @Size(max = 500) String email,
    @Size(max = 1000) String address,
    @Size(max = 500) String workplace,
    @NotBlank @Size(max = 500) String departmentName,
    @NotBlank @Size(max = 500) String positionName,
    @Size(max = 2000) String note,
    @NotNull UUID batchDayId,
    @NotNull @PositiveOrZero Long rowVersion) {

  /** Maps the transport body to the application command. */
  public UpdateParticipantCommand toCommand() {
    return new UpdateParticipantCommand(
        fullName,
        dateOfBirth,
        sex,
        identificationNumber,
        identificationIssueDate,
        identificationIssuePlace,
        ethnicity,
        phone,
        email,
        address,
        workplace,
        departmentName,
        positionName,
        note,
        batchDayId,
        rowVersion);
  }
}
