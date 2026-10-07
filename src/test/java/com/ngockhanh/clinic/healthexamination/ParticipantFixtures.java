package com.ngockhanh.clinic.healthexamination;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantImportRow;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Synthetic principals, rows and summaries for the Participant roster tests. */
public final class ParticipantFixtures {
  public static final String READ = "PARTICIPANT_VIEW";
  public static final String TEMPLATE_DOWNLOAD = "PARTICIPANT_TEMPLATE_DOWNLOAD";
  public static final String IMPORT = "PARTICIPANT_IMPORT";
  public static final String CREATE = "PARTICIPANT_CREATE";
  public static final String UPDATE = "PARTICIPANT_UPDATE";
  public static final String REMOVE = "PARTICIPANT_REMOVE";
  public static final String REACTIVATE = "PARTICIPANT_REACTIVATE";

  private ParticipantFixtures() {}

  public static UserPrincipal staff(String... permissions) {
    return UserPrincipal.builder()
        .userId(UUID.randomUUID())
        .staffId(UUID.randomUUID())
        .username("staff")
        .principalType("STAFF")
        .roleAssignments(
            List.of(
                new UserPrincipal.Assignment(
                    UUID.randomUUID(), "CLINIC_MANAGER", List.of(permissions))))
        .build();
  }

  public static UserPrincipal patient() {
    return UserPrincipal.builder()
        .userId(UUID.randomUUID())
        .patientId(UUID.randomUUID())
        .username("patient")
        .principalType("PATIENT")
        .roleAssignments(
            List.of(new UserPrincipal.Assignment(UUID.randomUUID(), "USER", List.of(READ, IMPORT))))
        .build();
  }

  public static ParticipantImportRow row(int rowNumber, String identification, LocalDate day) {
    return new ParticipantImportRow(
        rowNumber,
        null,
        "Synthetic Person " + rowNumber,
        LocalDate.of(1990, 1, 31),
        "FEMALE",
        identification,
        "0900000000",
        null,
        "Department",
        "Position",
        day);
  }

  public static ParticipantSummary summary(String identification) {
    return ParticipantSummary.builder()
        .id(UUID.randomUUID())
        .batchId(UUID.randomUUID())
        .batchDayId(UUID.randomUUID())
        .examinationDate(LocalDate.of(2026, 10, 4))
        .fullName("Synthetic Person")
        .dateOfBirth(LocalDate.of(1990, 1, 31))
        .sex("MALE")
        .identificationNumber(identification)
        .departmentName("Department")
        .positionName("Position")
        .rosterStatus("ACTIVE")
        .attendanceStatus("UNCONFIRMED")
        .reconciliationStatus("PENDING")
        .rowVersion(0)
        .build();
  }
}
