package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.Set;

public record Organization(
    AggregateId id,
    String code,
    String name,
    String organizationType,
    String taxCode,
    String phone,
    String email,
    String address,
    String contactFullName,
    String contactPosition,
    String contactPhone,
    String contactEmail,
    String status,
    long rowVersion) {
  public Organization {
    if (id == null
        || rowVersion < 0
        || !Set.of("ACTIVE", "INACTIVE").contains(status == null ? "" : status)
        || !Set.of("COMPANY", "SCHOOL", "GOVERNMENT", "OTHER")
            .contains(organizationType == null ? "" : organizationType))
      throw new IllegalArgumentException("Invalid organization");
    code = required(code, 50);
    name = required(name, 300);
    phone = required(phone, Integer.MAX_VALUE);
    email = required(email, Integer.MAX_VALUE);
    address = required(address, Integer.MAX_VALUE);
    contactFullName = required(contactFullName, 200);
    contactPhone = required(contactPhone, Integer.MAX_VALUE);
    contactEmail = required(contactEmail, Integer.MAX_VALUE);
    taxCode = optional(taxCode, 50);
    contactPosition = optional(contactPosition, 200);
  }

  private static String required(String value, int length) {
    if (value == null || value.isBlank() || value.trim().length() > length)
      throw new IllegalArgumentException("Missing organization details");
    return value.trim();
  }

  private static String optional(String value, int length) {
    if (value == null || value.isBlank()) return null;
    if (value.trim().length() > length)
      throw new IllegalArgumentException("Invalid organization details");
    return value.trim();
  }

  public static Organization create(
      AggregateId id,
      String code,
      String name,
      String organizationType,
      String taxCode,
      String phone,
      String email,
      String address,
      String contactFullName,
      String contactPosition,
      String contactPhone,
      String contactEmail) {
    return new Organization(
        id,
        code,
        name,
        organizationType,
        taxCode,
        phone,
        email,
        address,
        contactFullName,
        contactPosition,
        contactPhone,
        contactEmail,
        "ACTIVE",
        0);
  }

  public static Organization restore(
      AggregateId id,
      String code,
      String name,
      String organizationType,
      String taxCode,
      String phone,
      String email,
      String address,
      String contactFullName,
      String contactPosition,
      String contactPhone,
      String contactEmail,
      String status,
      long rowVersion) {
    return new Organization(
        id,
        code,
        name,
        organizationType,
        taxCode,
        phone,
        email,
        address,
        contactFullName,
        contactPosition,
        contactPhone,
        contactEmail,
        status,
        rowVersion);
  }

  public Organization updateDetails(
      String code,
      String name,
      String organizationType,
      String taxCode,
      String phone,
      String email,
      String address,
      String contactFullName,
      String contactPosition,
      String contactPhone,
      String contactEmail) {
    return new Organization(
        id,
        code,
        name,
        organizationType,
        taxCode,
        phone,
        email,
        address,
        contactFullName,
        contactPosition,
        contactPhone,
        contactEmail,
        status,
        rowVersion);
  }

  public Organization deactivate() {
    return new Organization(
        id,
        code,
        name,
        organizationType,
        taxCode,
        phone,
        email,
        address,
        contactFullName,
        contactPosition,
        contactPhone,
        contactEmail,
        "INACTIVE",
        rowVersion);
  }
}
