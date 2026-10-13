package com.ngockhanh.clinic.accesscontrol.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Query parameters of the account owner candidate search (UC-005). */
@Getter
@Setter
@NoArgsConstructor
public class AccountOwnerCandidateSearchRequest {
  @NotNull(message = "Account type is required")
  @Pattern(regexp = "STAFF|PATIENT", message = "Account type must be STAFF or PATIENT")
  private String accountType;

  @NotBlank(message = "Keyword is required")
  @Size(min = 2, max = 100, message = "Keyword must have 2 to 100 characters")
  private String keyword;

  @Override
  public String toString() {
    return "AccountOwnerCandidateSearchRequest[accountType=" + accountType + "]";
  }
}
