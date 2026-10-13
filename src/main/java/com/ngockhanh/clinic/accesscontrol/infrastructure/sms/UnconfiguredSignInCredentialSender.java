package com.ngockhanh.clinic.accesscontrol.infrastructure.sms;

import com.ngockhanh.clinic.accesscontrol.application.port.out.SignInCredentialSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stand-in until an SMS gateway is integrated (ADR-0017): it sends nothing and reports the message
 * as not delivered, so callers show the "SMS not sent" warning instead of a false success.
 */
@Slf4j
@Component
public class UnconfiguredSignInCredentialSender implements SignInCredentialSender {
  @Override
  public Boolean send(SignInCredentialMessage message) {
    log.warn(
        "Sign-in credential SMS not sent; no SMS gateway configured: accountId={}",
        message.accountId());
    return false;
  }
}
