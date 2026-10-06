package com.ngockhanh.clinic.accesscontrol.application.command;

import java.util.UUID;
import lombok.Builder;

/**
 * Sign-in attempt. {@code previousSessionId} is the browser's existing session cookie, if any; it
 * is ended before a new session is issued.
 */
@Builder
public record LoginCommand(
    String username, String password, String previousSessionId, UUID correlationId) {
  @Override
  public String toString() {
    return "LoginCommand[correlationId=" + correlationId + "]";
  }
}
