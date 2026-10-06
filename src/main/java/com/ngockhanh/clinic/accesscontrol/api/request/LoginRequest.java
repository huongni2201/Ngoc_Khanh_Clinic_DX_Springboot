package com.ngockhanh.clinic.accesscontrol.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Sign-in credentials, used exactly as submitted (no trimming). The password bound only limits
 * payload size; the 72-byte hashing limit is enforced as an invalid sign-in.
 */
@Builder
public record LoginRequest(
    @NotBlank @Size(max = 150) String username, @NotNull @Size(max = 1024) String password) {
  @Override
  public String toString() {
    return "LoginRequest[credentials hidden]";
  }
}
