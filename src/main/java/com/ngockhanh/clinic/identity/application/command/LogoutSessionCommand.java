package com.ngockhanh.clinic.identity.application.command;

import java.util.List;
import java.util.UUID;

public record LogoutSessionCommand(List<String> sessionIds, UUID correlationId) {
  public LogoutSessionCommand {
    sessionIds = List.copyOf(sessionIds);
  }

  @Override
  public String toString() {
    return "LogoutSessionCommand[correlationId=" + correlationId + "]";
  }
}
