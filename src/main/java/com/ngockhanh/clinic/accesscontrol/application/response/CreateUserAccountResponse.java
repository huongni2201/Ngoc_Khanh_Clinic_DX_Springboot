package com.ngockhanh.clinic.accesscontrol.application.response;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Account created by an Administrator. The temporary password is never returned.
 *
 * @param accountId new account identifier
 * @param accountType STAFF or PATIENT
 * @param username sign-in identifier
 * @param status ACTIVE
 * @param ownerId staff member or patient that owns the account
 * @param roleCodes codes of the granted roles
 * @param smsDelivered true only when the SMS gateway accepted the sign-in information
 */
@Builder
public record CreateUserAccountResponse(
    UUID accountId,
    String accountType,
    String username,
    String status,
    UUID ownerId,
    List<String> roleCodes,
    boolean smsDelivered) {
  public CreateUserAccountResponse {
    roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
  }
}
