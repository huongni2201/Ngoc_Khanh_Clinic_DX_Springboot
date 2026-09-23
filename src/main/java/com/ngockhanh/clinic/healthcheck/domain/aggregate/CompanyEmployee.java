package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;

import com.ngockhanh.clinic.healthcheck.domain.exception.PatientRelinkForbidden;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import java.time.LocalDate;

public final class CompanyEmployee {
    private final UUID id;
    private final UUID companyId;
    private final String employeeCode;
    private final IdentificationNumber identificationNumber;
    private final String fullName;
    private final LocalDate dateOfBirth;
    private final String sex;
    private UUID patientId;

    public CompanyEmployee(UUID companyId, String employeeCode, IdentificationNumber identificationNumber, String fullName, LocalDate dateOfBirth, String sex) {
        this(null, companyId, employeeCode, identificationNumber, fullName, dateOfBirth, sex);
    }

    public CompanyEmployee(UUID id, UUID companyId, String employeeCode, IdentificationNumber identificationNumber, String fullName, LocalDate dateOfBirth, String sex) {
        if (id == null || companyId == null || employeeCode == null || employeeCode.isBlank() || identificationNumber == null
                || fullName == null || fullName.isBlank() || dateOfBirth == null || sex == null || sex.isBlank()) {
            throw new IllegalArgumentException("Missing roster identity");
        }
        this.id = id;
        this.companyId = companyId;
        this.employeeCode = employeeCode;
        this.identificationNumber = identificationNumber;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.sex = sex;
    }

    public static CompanyEmployee restore(UUID id, UUID companyId, String employeeCode, IdentificationNumber identificationNumber,
                                          String fullName, LocalDate dateOfBirth, String sex, UUID patientId) {
        if (id == null) throw new IllegalArgumentException("Persisted employee requires ID");
        CompanyEmployee employee = new CompanyEmployee(id, companyId, employeeCode, identificationNumber, fullName, dateOfBirth, sex);
        if (patientId != null) employee.linkPatient(patientId);
        return employee;
    }

    public UUID patientId() {
        return patientId;
    }

    public UUID id() { return id; }

    public void linkPatient(UUID patientId) {
        if (patientId == null) throw new IllegalArgumentException("Invalid Patient ID");
        if (this.patientId != null && this.patientId != patientId) throw new PatientRelinkForbidden();
        this.patientId = patientId;
    }

    public CompanyEmployee reimport(String employeeCode, IdentificationNumber identificationNumber, String fullName, LocalDate dateOfBirth, String sex) {
        if (patientId != null && (!this.employeeCode.equals(employeeCode) || !this.identificationNumber.equals(identificationNumber))) {
            throw new DomainRuleViolation("Linked roster identity requires review");
        }
        CompanyEmployee updated = new CompanyEmployee(id, companyId, employeeCode, identificationNumber, fullName, dateOfBirth, sex);
        updated.patientId = patientId;
        return updated;
    }

    public UUID companyId() { return companyId; }
    public String employeeCode() { return employeeCode; }
    public IdentificationNumber identificationNumber() { return identificationNumber; }
    public String fullName() { return fullName; }
    public LocalDate dateOfBirth() { return dateOfBirth; }
    public String sex() { return sex; }
}

