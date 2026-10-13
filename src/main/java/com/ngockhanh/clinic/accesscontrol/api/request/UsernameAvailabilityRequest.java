package com.ngockhanh.clinic.accesscontrol.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Query parameter of the username availability check (UC-005). */
@Getter
@Setter
@NoArgsConstructor
public class UsernameAvailabilityRequest {
  @NotNull(message = "Username is required")
  @Size(min = 1, max = 150, message = "Username must have 1 to 150 characters")
  @Pattern(
      regexp = UsernameRules.PATTERN,
      message = "Username must use letters without diacritics, digits or symbols")
  private String username;

  @Override
  public String toString() {
    return "UsernameAvailabilityRequest[username hidden]";
  }
}
