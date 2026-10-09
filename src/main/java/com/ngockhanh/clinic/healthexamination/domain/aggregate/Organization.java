package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;

/** Aggregate root for an organization and its identity, contact, and lifecycle invariants. */
public class Organization {
  private final AggregateId id;
  private String name;
  private String taxCode;
  private String phone;
  private String email;
  private String address;
  private String contactFullName;
  private String contactPhone;
  private String contactEmail;
  private OrganizationStatus status;
  private final long rowVersion;

  private Organization(
      AggregateId id,
      String name,
      String taxCode,
      String phone,
      String email,
      String address,
      String contactFullName,
      String contactPhone,
      String contactEmail,
      OrganizationStatus status,
      long rowVersion) {
    if (id == null || status == null || rowVersion < 0)
      throw new IllegalArgumentException("Invalid organization");
    this.id = id;
    this.name = required(name, 300);
    this.taxCode = optional(taxCode, 50);
    this.phone = optional(phone, Integer.MAX_VALUE);
    this.email = required(email, Integer.MAX_VALUE);
    this.address = required(address, Integer.MAX_VALUE);
    this.contactFullName = required(contactFullName, 200);
    this.contactPhone = required(contactPhone, Integer.MAX_VALUE);
    this.contactEmail = required(contactEmail, Integer.MAX_VALUE);
    this.status = status;
    this.rowVersion = rowVersion;
  }

  public static Organization create(
      AggregateId id,
      String name,
      String taxCode,
      String phone,
      String email,
      String address,
      String contactFullName,
      String contactPhone,
      String contactEmail) {
    return new Organization(
        id,
        name,
        taxCode,
        phone,
        email,
        address,
        contactFullName,
        contactPhone,
        contactEmail,
        OrganizationStatus.ACTIVE,
        0);
  }

  public static Organization restore(
      AggregateId id,
      String name,
      String taxCode,
      String phone,
      String email,
      String address,
      String contactFullName,
      String contactPhone,
      String contactEmail,
      OrganizationStatus status,
      long rowVersion) {
    return new Organization(
        id,
        name,
        taxCode,
        phone,
        email,
        address,
        contactFullName,
        contactPhone,
        contactEmail,
        status,
        rowVersion);
  }

  /** Replaces mutable organization details while preserving aggregate identity and lifecycle. */
  public void updateDetails(
      String name,
      String taxCode,
      String phone,
      String email,
      String address,
      String contactFullName,
      String contactPhone,
      String contactEmail) {
    String normalizedName = required(name, 300);
    String normalizedTaxCode = optional(taxCode, 50);
    String normalizedPhone = optional(phone, Integer.MAX_VALUE);
    String normalizedEmail = required(email, Integer.MAX_VALUE);
    String normalizedAddress = required(address, Integer.MAX_VALUE);
    String normalizedContactFullName = required(contactFullName, 200);
    String normalizedContactPhone = required(contactPhone, Integer.MAX_VALUE);
    String normalizedContactEmail = required(contactEmail, Integer.MAX_VALUE);

    this.name = normalizedName;
    this.taxCode = normalizedTaxCode;
    this.phone = normalizedPhone;
    this.email = normalizedEmail;
    this.address = normalizedAddress;
    this.contactFullName = normalizedContactFullName;
    this.contactPhone = normalizedContactPhone;
    this.contactEmail = normalizedContactEmail;
  }

  public void deactivate() {
    status = OrganizationStatus.INACTIVE;
  }

  public AggregateId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String taxCode() {
    return taxCode;
  }

  public String phone() {
    return phone;
  }

  public String email() {
    return email;
  }

  public String address() {
    return address;
  }

  public String contactFullName() {
    return contactFullName;
  }

  public String contactPhone() {
    return contactPhone;
  }

  public String contactEmail() {
    return contactEmail;
  }

  public OrganizationStatus status() {
    return status;
  }

  public long rowVersion() {
    return rowVersion;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof Organization organization && id.equals(organization.id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  private static String required(String value, int maxLength) {
    if (value == null || value.isBlank() || value.trim().length() > maxLength)
      throw new IllegalArgumentException("Missing organization details");
    return value.trim();
  }

  private static String optional(String value, int maxLength) {
    if (value == null || value.isBlank()) return null;
    if (value.trim().length() > maxLength)
      throw new IllegalArgumentException("Invalid organization details");
    return value.trim();
  }
}
