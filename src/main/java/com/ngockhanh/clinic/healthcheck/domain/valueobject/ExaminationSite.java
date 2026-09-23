package com.ngockhanh.clinic.healthcheck.domain.valueobject;

public record ExaminationSite(String type, String name, String address) {
    public ExaminationSite {
        if ((!"CLINIC".equals(type) && !"COMPANY".equals(type)) || name == null || name.isBlank()
                || ("COMPANY".equals(type) && (address == null || address.isBlank()))) {
            throw new IllegalArgumentException("Invalid examination site");
        }
    }
}
