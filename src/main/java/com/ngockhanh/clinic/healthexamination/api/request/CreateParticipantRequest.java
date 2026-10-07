package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.CreateParticipantCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Body for adding one Participant to a batch by hand. Only the structure is checked here; the
 * roster rules live in the domain.
 */
@Builder
public record CreateParticipantRequest(
    @Size(max = 500) String participantCode,
    @NotBlank @Size(max = 200) String fullName,
    @NotNull LocalDate dateOfBirth,
    @NotNull @Pattern(regexp = "MALE|FEMALE|OTHER|UNKNOWN") String sex,
    @NotBlank @Pattern(regexp = "\\d{1,20}") String identificationNumber,
    @Size(max = 500) String phone,
    @Size(max = 500) String email,
    @NotBlank @Size(max = 500) String departmentName,
    @NotBlank @Size(max = 500) String positionName,
    @NotNull UUID batchDayId) {

  /** Maps the transport body to the application command. */
  public CreateParticipantCommand toCommand() {
    return new CreateParticipantCommand(
        participantCode,
        fullName,
        dateOfBirth,
        sex,
        identificationNumber,
        phone,
        email,
        departmentName,
        positionName,
        batchDayId);
  }
}
