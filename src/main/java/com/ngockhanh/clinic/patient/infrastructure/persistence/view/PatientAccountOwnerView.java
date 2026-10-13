package com.ngockhanh.clinic.patient.infrastructure.persistence.view;

import java.time.LocalDate;
import java.util.UUID;

/** SQL projection of a patient as an account owner or eligible owner. */
public record PatientAccountOwnerView(
    UUID id,
    String patientCode,
    String fullName,
    LocalDate dateOfBirth,
    String phone,
    String status) {}
