package com.ngockhanh.clinic.accesscontrol.domain.aggregate;

import com.ngockhanh.clinic.accesscontrol.domain.enums.Sex;
import com.ngockhanh.clinic.accesscontrol.domain.enums.StaffMemberStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Clinic staff member profile. Created {@code ACTIVE} with a generated staff code; department and
 * room are not assigned by the current contract.
 */
public final class StaffMember {
  public static final Integer STAFF_CODE_MAX_LENGTH = 30;
  public static final Integer FULL_NAME_MAX_LENGTH = 200;
  public static final Integer PHONE_MAX_LENGTH = 30;
  public static final Integer EMAIL_MAX_LENGTH = 255;

  private final UUID id;
  private final String staffCode;
  private final String fullName;
  private final LocalDate dateOfBirth;
  private final Sex sex;
  private final String phone;
  private final String email;
  private final StaffMemberStatus status;
  private final Long rowVersion;

  private StaffMember(
      UUID id,
      String staffCode,
      String fullName,
      LocalDate dateOfBirth,
      Sex sex,
      String phone,
      String email,
      StaffMemberStatus status,
      Long rowVersion) {
    if (id == null || status == null || rowVersion == null || rowVersion < 0)
      throw new IllegalArgumentException("Invalid staff member");
    this.id = id;
    this.staffCode = required(staffCode, STAFF_CODE_MAX_LENGTH, "Staff code");
    this.fullName = required(fullName, FULL_NAME_MAX_LENGTH, "Full name");
    if (this.fullName.codePoints().noneMatch(Character::isLetter))
      throw new IllegalArgumentException("Full name must contain a letter");
    this.dateOfBirth = dateOfBirth;
    this.sex = sex;
    this.phone = optional(phone, PHONE_MAX_LENGTH, "Phone");
    this.email = optional(email, EMAIL_MAX_LENGTH, "Email");
    this.status = status;
    this.rowVersion = rowVersion;
  }

  /**
   * Creates an active staff member with row version 0.
   *
   * @param today current clinic date; a date of birth must be before it
   * @throws IllegalArgumentException when a value is missing or too long, or the birth date is not
   *     in the past
   */
  public static StaffMember create(
      UUID id,
      String staffCode,
      String fullName,
      LocalDate dateOfBirth,
      Sex sex,
      String phone,
      String email,
      LocalDate today) {
    if (today == null) throw new IllegalArgumentException("Current date is required");
    if (dateOfBirth != null && !dateOfBirth.isBefore(today))
      throw new IllegalArgumentException("Date of birth must be in the past");
    return new StaffMember(
        id, staffCode, fullName, dateOfBirth, sex, phone, email, StaffMemberStatus.ACTIVE, 0L);
  }

  public UUID id() {
    return id;
  }

  public String staffCode() {
    return staffCode;
  }

  public String fullName() {
    return fullName;
  }

  public LocalDate dateOfBirth() {
    return dateOfBirth;
  }

  public Sex sex() {
    return sex;
  }

  public String phone() {
    return phone;
  }

  public String email() {
    return email;
  }

  public StaffMemberStatus status() {
    return status;
  }

  public Long rowVersion() {
    return rowVersion;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof StaffMember staffMember && id.equals(staffMember.id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  private static String required(String value, Integer maxLength, String label) {
    if (value == null || value.isBlank() || value.trim().length() > maxLength)
      throw new IllegalArgumentException(label + " is missing or too long");
    return value.trim();
  }

  private static String optional(String value, Integer maxLength, String label) {
    if (value == null || value.isBlank()) return null;
    if (value.trim().length() > maxLength)
      throw new IllegalArgumentException(label + " is too long");
    return value.trim();
  }
}
