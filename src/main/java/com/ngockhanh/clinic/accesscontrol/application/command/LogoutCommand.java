package com.ngockhanh.clinic.accesscontrol.application.command;

import java.util.UUID;
import lombok.Builder;

/** Sign-out of the session identified by the browser's session cookie, which may be absent. */
@Builder
public record LogoutCommand(String sessionId, UUID correlationId) {
  @Override
  public String toString() {
    return "LogoutCommand[correlationId=" + correlationId + "]";
  }
}
