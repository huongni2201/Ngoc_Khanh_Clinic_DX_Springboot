package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthcheck.domain.exception.PatientRelinkForbidden;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.Cccd;

public final class CompanyEmployee {
    private final UUID id;
    private final UUID companyId;
    private final String employeeCode;
    private final Cccd cccd;
    private final String fullName;
    private final LocalDate dateOfBirth;
    private final String sex;
    private final String departmentName;
    private final String jobTitle;
    private final String occupation;
    private final String status;
    private UUID patientId;

    private CompanyEmployee(UUID id, UUID companyId, String employeeCode, Cccd cccd,
                            String fullName, LocalDate dateOfBirth, String sex, String departmentName,
                            String jobTitle, String occupation, String status) {
        if (id == null || companyId == null || employeeCode == null || employeeCode.isBlank() || cccd == null
                || fullName == null || fullName.isBlank() || dateOfBirth == null || sex == null || sex.isBlank()
                || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Missing roster identity");
        }
        this.id = id;
        this.companyId = companyId;
        this.employeeCode = employeeCode;
        this.cccd = cccd;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.sex = sex;
        this.departmentName = departmentName;
        this.jobTitle = jobTitle;
        this.occupation = occupation;
        this.status = status;
    }

    public static CompanyEmployee create(UUID id, UUID companyId, String employeeCode, Cccd cccd,
                                         String fullName, LocalDate dateOfBirth, String sex) {
        return create(id, companyId, employeeCode, cccd, fullName, dateOfBirth, sex, null, null, null);
    }

    public static CompanyEmployee create(UUID id, UUID companyId, String employeeCode, Cccd cccd,
                                         String fullName, LocalDate dateOfBirth, String sex, String departmentName,
                                         String jobTitle, String occupation) {
        return new CompanyEmployee(id, companyId, employeeCode, cccd, fullName, dateOfBirth, sex,
                departmentName, jobTitle, occupation, "ACTIVE");
    }

    public static CompanyEmployee restore(UUID id, UUID companyId, String employeeCode, Cccd cccd,
                                          String fullName, LocalDate dateOfBirth, String sex, String departmentName,
                                          String jobTitle, String occupation, String status, UUID patientId) {
        CompanyEmployee employee = new CompanyEmployee(id, companyId, employeeCode, cccd, fullName,
                dateOfBirth, sex, departmentName, jobTitle, occupation, status);
        employee.patientId = patientId;
        return employee;
    }

    public static CompanyEmployee restore(UUID id, UUID companyId, String employeeCode, Cccd cccd,
                                          String fullName, LocalDate dateOfBirth, String sex, UUID patientId) {
        return restore(id, companyId, employeeCode, cccd, fullName, dateOfBirth, sex,
                null, null, null, "ACTIVE", patientId);
    }

    public void linkPatient(UUID patientId) {
        if (patientId == null) throw new IllegalArgumentException("Invalid Patient ID");
        if (this.patientId != null && !Objects.equals(this.patientId, patientId)) throw new PatientRelinkForbidden();
        this.patientId = patientId;
    }

    public CompanyEmployee reimport(String employeeCode, Cccd cccd, String fullName,
                                    LocalDate dateOfBirth, String sex) {
        return reimport(employeeCode, cccd, fullName, dateOfBirth, sex,
                departmentName, jobTitle, occupation);
    }

    public CompanyEmployee reimport(String employeeCode, Cccd cccd, String fullName,
                                    LocalDate dateOfBirth, String sex, String departmentName,
                                    String jobTitle, String occupation) {
        if (patientId != null && (!this.employeeCode.equals(employeeCode) || !this.cccd.equals(cccd))) {
            throw new DomainRuleViolation("Linked roster identity requires review");
        }
        CompanyEmployee updated = new CompanyEmployee(id, companyId, employeeCode, cccd, fullName,
                dateOfBirth, sex, departmentName, jobTitle, occupation, status);
        updated.patientId = patientId;
        return updated;
    }

    public UUID id() { return id; }
    public UUID companyId() { return companyId; }
    public String employeeCode() { return employeeCode; }
    public Cccd cccd() { return cccd; }
    public String fullName() { return fullName; }
    public LocalDate dateOfBirth() { return dateOfBirth; }
    public String sex() { return sex; }
    public String departmentName() { return departmentName; }
    public String jobTitle() { return jobTitle; }
    public String occupation() { return occupation; }
    public String status() { return status; }
    public UUID patientId() { return patientId; }
}