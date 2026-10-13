package com.ngockhanh.clinic.accesscontrol.api.request;

/** Structural username rules shared by the account creation requests. */
final class UsernameRules {
  /**
   * Printable ASCII (no diacritics), without leading or trailing spaces. Matches the accounts table
   * constraint {@code ck_accounts_username_trimmed} and the sign-in rules of UC-001.
   */
  static final String PATTERN = "^[\\x21-\\x7E](?:[\\x20-\\x7E]*[\\x21-\\x7E])?$";

  private UsernameRules() {}
}
