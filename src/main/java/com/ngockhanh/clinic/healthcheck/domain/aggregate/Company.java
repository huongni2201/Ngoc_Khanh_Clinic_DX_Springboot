package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.UUID;

public final class Company {
    private final UUID id;
    private final String code;
    private final String name;
    private final String taxCode;
    private final String address;
    private final String contactName;
    private final String contactPhone;
    private final String contactJobTitle;
    private final String note;
    private final String status;

    private Company(UUID id, String code, String name, String taxCode, String address, String contactName,
                    String contactPhone, String contactJobTitle, String note, String status) {
        if (id == null || code == null || code.isBlank() || name == null || name.isBlank()
                || contactName == null || contactName.isBlank() || contactPhone == null || contactPhone.isBlank()
                || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Missing company details");
        }
        this.id = id;
        this.code = code;
        this.name = name;
        this.taxCode = taxCode;
        this.address = address;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactJobTitle = contactJobTitle;
        this.note = note;
        this.status = status;
    }

    public static Company create(UUID id, String code, String name, String contactName, String contactPhone) {
        return create(id, code, name, null, null, contactName, contactPhone, null, null);
    }

    public static Company create(UUID id, String code, String name, String taxCode, String address,
                                  String contactName, String contactPhone, String contactJobTitle, String note) {
        return new Company(id, code, name, taxCode, address, contactName, contactPhone,
                contactJobTitle, note, "ACTIVE");
    }

    public static Company restore(UUID id, String code, String name, String taxCode, String address,
                                   String contactName, String contactPhone, String contactJobTitle,
                                   String note, String status) {
        return new Company(id, code, name, taxCode, address, contactName, contactPhone,
                contactJobTitle, note, status);
    }

    public UUID id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public String taxCode() { return taxCode; }
    public String address() { return address; }
    public String contactName() { return contactName; }
    public String contactPhone() { return contactPhone; }
    public String contactJobTitle() { return contactJobTitle; }
    public String note() { return note; }
    public String status() { return status; }
}