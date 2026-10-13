package com.ngockhanh.clinic.accesscontrol.application.port.out.owner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patient profiles as account owners. Implemented by the patient module, which owns the patients
 * table, so access control reads patients without depending on that module.
 */
public interface PatientAccountOwnerDirectory {
  /**
   * Reads a patient and holds a share lock on it until the end of the caller's transaction.
   *
   * @param patientId patient identifier
   * @return the patient, or empty
   */
  Optional<PatientAccountOwner> lockForAccountLink(UUID patientId);

  /**
   * Finds active patients whose patient code, full name or phone number contains the keyword, or
   * whose identification number equals it. LIKE wildcards in the keyword are matched literally.
   *
   * @param keyword search text
   * @param limit maximum number of rows
   * @return candidates ordered by full name, including patients that already have an account
   */
  List<PatientOwnerCandidate> searchActive(String keyword, int limit);

  /**
   * Patient as needed to link an account.
   *
   * @param id patient identifier
   * @param active whether the patient is in ACTIVE status
   * @param phone phone number as stored, or null
   */
  record PatientAccountOwner(UUID id, boolean active, String phone) {
    @Override
    public String toString() {
      return "PatientAccountOwner[id=" + id + ", active=" + active + "]";
    }
  }

  /**
   * Patient offered as an account owner.
   *
   * @param id patient identifier
   * @param patientCode patient code
   * @param fullName full name
   * @param dateOfBirth date of birth
   * @param phone phone number as stored, or null
   */
  record PatientOwnerCandidate(
      UUID id, String patientCode, String fullName, LocalDate dateOfBirth, String phone) {}
}
