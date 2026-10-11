package com.ngockhanh.clinic.healthexamination.domain.valueobject;

import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;

public record ExaminationSite(ExaminationSiteType type, String address) {
  public ExaminationSite {
    if (type == null
        || (type == ExaminationSiteType.ORGANIZATION_SITE
            && (address == null || address.isBlank())))
      throw new IllegalArgumentException("Invalid examination site");
    if (type == ExaminationSiteType.CLINIC) address = "";
  }
}
