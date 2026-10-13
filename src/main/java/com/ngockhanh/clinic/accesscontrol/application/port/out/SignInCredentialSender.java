package com.ngockhanh.clinic.accesscontrol.application.port.out;

import java.util.UUID;

/**
 * Sends the sign-in information of a new account to its owner by SMS (UC-005, ADR-0017). Called
 * after the account is committed; the temporary password is never stored for later delivery.
 */
public interface SignInCredentialSender {
  /**
   * Sends the message once.
   *
   * @param message recipient and sign-in information
   * @return true only when the SMS gateway accepted the message
   */
  Boolean send(SignInCredentialMessage message);

  /**
   * Sign-in information for one account. {@link #toString()} hides everything but the account id.
   *
   * @param accountId new account identifier
   * @param phoneNumber normalized mobile number of the owner
   * @param username sign-in identifier
   * @param temporaryPassword temporary password in plain text
   */
  record SignInCredentialMessage(
      UUID accountId, String phoneNumber, String username, String temporaryPassword) {
    @Override
    public String toString() {
      return "SignInCredentialMessage[accountId=" + accountId + "]";
    }
  }
}
