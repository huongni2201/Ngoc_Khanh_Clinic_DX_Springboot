package com.ngockhanh.clinic.healthexamination.domain.valueobject;

import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;

public record ExaminationSite(ExaminationSiteType type, String name, String address) {
    public ExaminationSite {
        if (type == null || name == null || name.isBlank() || name.length() > 250
                || (address != null && address.length() > 500)) {
            throw new IllegalArgumentException("Invalid examination site");
        }
    }
}