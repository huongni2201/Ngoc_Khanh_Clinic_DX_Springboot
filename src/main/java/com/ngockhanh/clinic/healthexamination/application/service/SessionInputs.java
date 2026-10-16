package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;

/** Parsing of the raw session fields shared by the session use cases. */
public final class SessionInputs {
  private SessionInputs() {}

  /**
   * @throws IllegalArgumentException when the value is missing or not a session activity
   */
  public static SessionActivity activity(String value) {
    if (value == null) throw new IllegalArgumentException("Session activity is required");
    return SessionActivity.valueOf(value);
  }

  /**
   * Builds the site of a session. The clinic has no address; an organization site needs one.
   *
   * @throws IllegalArgumentException when the type is missing or unknown, or an organization site
   *     has no address
   */
  public static ExaminationSite site(String type, String address) {
    if (type == null) throw new IllegalArgumentException("Session site type is required");
    return new ExaminationSite(
        ExaminationSiteType.valueOf(type), address == null ? null : address.trim());
  }
}
