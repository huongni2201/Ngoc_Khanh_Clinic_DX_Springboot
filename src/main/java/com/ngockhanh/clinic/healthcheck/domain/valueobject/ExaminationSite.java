package com.ngockhanh.clinic.healthcheck.domain.valueobject;

public record ExaminationSite(ExaminationSiteType type, String name, String address) {
    public ExaminationSite {
        if (type == null || name == null || name.isBlank()
                || (type == ExaminationSiteType.COMPANY && (address == null || address.isBlank()))) {
            throw new IllegalArgumentException("Invalid examination site");
        }
    }
}