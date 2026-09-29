package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

public final class Organization {
    private final AggregateId id;
    private final String name;
    private final String taxCode;
    private final String address;
    private final String contactName;
    private final String contactPhone;
    private final String contactJobTitle;
    private final String note;
    private final String status;
    private final long rowVersion;

    private Organization(AggregateId id, String name, String taxCode, String address, String contactName,
                         String contactPhone, String contactJobTitle, String note, String status, long rowVersion) {
        if (id == null || name == null || name.isBlank()
                || contactName == null || contactName.isBlank() || contactPhone == null || contactPhone.isBlank()
                || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Missing organization details");
        }
        this.id = id;
        this.name = name;
        this.taxCode = taxCode;
        this.address = address;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactJobTitle = contactJobTitle;
        this.note = note;
        this.status = status;
        this.rowVersion = rowVersion;
    }

    public static Organization create(AggregateId id, String name, String contactName, String contactPhone) {
        return create(id, name, null, null, contactName, contactPhone, null, null);
    }

    public static Organization create(AggregateId id, String name, String taxCode, String address,
                                      String contactName, String contactPhone, String contactJobTitle, String note) {
        return new Organization(id, name, taxCode, address, contactName, contactPhone,
                contactJobTitle, note, "ACTIVE", 0L);
    }

    public static Organization restore(AggregateId id, String name, String taxCode, String address,
                                       String contactName, String contactPhone, String contactJobTitle,
                                       String note, String status) {
        return restore(id, name, taxCode, address, contactName, contactPhone, contactJobTitle, note, status, 0L);
    }

    public static Organization restore(AggregateId id, String name, String taxCode, String address,
                                       String contactName, String contactPhone, String contactJobTitle,
                                       String note, String status, long rowVersion) {
        return new Organization(id, name, taxCode, address, contactName, contactPhone,
                contactJobTitle, note, status, rowVersion);
    }

    public Organization updateDetails(String name, String taxCode, String address,
                                      String contactName, String contactPhone, String contactJobTitle, String note) {
        return new Organization(id, name, taxCode, address, contactName, contactPhone,
                contactJobTitle, note, status, rowVersion);
    }

    public Organization deactivate() {
        return new Organization(id, name, taxCode, address, contactName, contactPhone,
                contactJobTitle, note, "INACTIVE", rowVersion);
    }

    public AggregateId id() { return id; }
    public String name() { return name; }
    public String taxCode() { return taxCode; }
    public String address() { return address; }
    public String contactName() { return contactName; }
    public String contactPhone() { return contactPhone; }
    public String contactJobTitle() { return contactJobTitle; }
    public String note() { return note; }
    public String status() { return status; }
    public long rowVersion() { return rowVersion; }
}
